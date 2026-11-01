package com.lazy.rest.mcp;

import com.lazy.rest.audit.AuditContext;
import com.lazy.rest.audit.AuditLog;
import com.lazy.rest.audit.AuditService;
import com.lazy.rest.common.error.ApiException;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.ConstraintViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Runs every MCP tool call through the same pipeline:
 * <ol>
 *   <li>bind the caller's {@link Authentication} (from the MCP transport context) to the thread,</li>
 *   <li>check tool-level permissions (e.g. {@code mcp:write}),</li>
 *   <li>mark the call as channel MCP so nested service-level audit entries say so,</li>
 *   <li>write one {@code MCP_TOOL_CALL} audit entry (SUCCESS / DENIED / FAILURE),</li>
 *   <li>translate failures into short, model-friendly messages (no stack traces, no internals).</li>
 * </ol>
 * Success values are returned as JSON text content (same JSON as the REST API); failures as a
 * tool result with {@code isError: true} and a single readable message the agent can act on.
 */
@Component
public class McpToolExecutor {

    private final AuditService auditService;
    private final JsonMapper jsonMapper;

    public McpToolExecutor(AuditService auditService, JsonMapper jsonMapper) {
        this.auditService = auditService;
        this.jsonMapper = jsonMapper;
    }

    public CallToolResult call(String tool, McpTransportContext transportContext, Map<String, ?> arguments,
            String requiredAuthority, Supplier<?> action) {
        try {
            Object value = execute(tool, transportContext, arguments, requiredAuthority, action);
            return CallToolResult.builder().addTextContent(jsonMapper.writeValueAsString(value)).build();
        } catch (McpToolException e) {
            return CallToolResult.builder().isError(true).addTextContent(e.getMessage()).build();
        }
    }

    private Object execute(String tool, McpTransportContext transportContext, Map<String, ?> arguments,
            String requiredAuthority, Supplier<?> action) {
        Authentication authentication = resolveAuthentication(transportContext);
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        AuditContext.set(AuditLog.Channel.MCP);
        long start = System.nanoTime();
        String detail = "args=" + summarize(arguments);
        try {
            if (requiredAuthority != null && !hasAuthority(authentication, requiredAuthority)) {
                throw new AccessDeniedException("Missing permission " + requiredAuthority);
            }
            Object result = action.get();
            audit(tool, AuditLog.Outcome.SUCCESS, detail, start);
            return result;
        } catch (AccessDeniedException e) {
            audit(tool, AuditLog.Outcome.DENIED, detail + " error=" + e.getMessage(), start);
            throw new McpToolException("Permission denied: " + e.getMessage()
                    + ". The API key or user behind this MCP connection is not allowed to do this.");
        } catch (ConstraintViolationException e) {
            String violations = e.getConstraintViolations().stream()
                    .map(v -> lastNode(v.getPropertyPath().toString()) + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            audit(tool, AuditLog.Outcome.FAILURE, detail + " error=validation", start);
            throw new McpToolException("Invalid arguments: " + violations);
        } catch (ApiException e) {
            audit(tool, AuditLog.Outcome.FAILURE, detail + " error=" + e.getCode(), start);
            throw new McpToolException(e.getMessage());
        } catch (RuntimeException e) {
            audit(tool, AuditLog.Outcome.FAILURE, detail + " error=" + e.getClass().getSimpleName(), start);
            throw new McpToolException("Internal error while running " + tool + ". Try again later.");
        } finally {
            AuditContext.clear();
            SecurityContextHolder.setContext(previous);
        }
    }

    private void audit(String tool, AuditLog.Outcome outcome, String detail, long startNanos) {
        auditService.record(null, "MCP_TOOL_CALL", "mcp-tool", tool, outcome, detail,
                (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static Authentication resolveAuthentication(McpTransportContext transportContext) {
        Object fromTransport = transportContext != null ? transportContext.get(McpServerConfig.AUTHENTICATION_KEY) : null;
        Authentication authentication = fromTransport instanceof Authentication a
                ? a : SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            // Unreachable in practice: the MCP filter chain rejects unauthenticated requests.
            throw new McpToolException("Not authenticated");
        }
        return authentication;
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }

    private static String summarize(Map<String, ?> arguments) {
        String text = arguments.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + abbreviate(String.valueOf(e.getValue()), 60))
                .collect(Collectors.joining(", ", "{", "}"));
        return abbreviate(text, 400);
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}

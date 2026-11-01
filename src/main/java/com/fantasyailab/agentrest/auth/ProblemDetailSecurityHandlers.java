package com.fantasyailab.agentrest.auth;

import com.fantasyailab.agentrest.common.error.ProblemDetailWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401/403 responses produced by the security filter chain, rendered as problem+json with an RFC 6750
 * {@code WWW-Authenticate} header. (Spring Security's default entry point would also advertise an
 * RFC 9728 {@code resource_metadata} URL; AgentREST does not offer OAuth authorization for clients yet,
 * so it is deliberately left out to avoid sending OAuth-capable MCP clients into a dead end.)
 */
@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    public ProblemDetailSecurityHandlers(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        String detail;
        if (ex instanceof InvalidBearerTokenException) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer error=\"invalid_token\", error_description=\"Invalid or expired access token\"");
            detail = "Invalid or expired access token";
        } else if (ex instanceof BadCredentialsException && ex.getMessage() != null && ex.getMessage().contains("API key")) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            detail = "Invalid API key";
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            detail = "Authentication required: send 'Authorization: Bearer <token>'"
                    + (request.getRequestURI().startsWith("/mcp") ? " or 'X-API-Key: <key>'" : "");
        }
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", detail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
        writer.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to access this resource");
    }
}

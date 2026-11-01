package com.fantasyailab.agentrest.auth.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates {@code X-API-Key: ar_...} or {@code Authorization: Bearer ar_...} (legacy {@code lr_...} keys too). Only installed in
 * the MCP security filter chain. Intentionally not a Spring bean, so Boot does not also register it
 * as a global servlet filter.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final AuthenticationEntryPoint entryPoint;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, AuthenticationEntryPoint entryPoint) {
        this.apiKeyService = apiKeyService;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawKey = resolveKey(request);
        if (rawKey == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(apiKeyService.authenticate(rawKey));
            SecurityContextHolder.setContext(context);
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, e);
            return;
        }
        chain.doFilter(request, response);
    }

    static String resolveKey(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (ApiKeyService.hasApiKeyPrefix(token)) {
                return token;
            }
        }
        return null;
    }
}

package com.lazy.rest.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Small helpers around the current {@link Authentication}, independent of how the caller authenticated. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Authentication> currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth);
    }

    public static Optional<String> currentUsername() {
        return currentAuthentication().map(Authentication::getName);
    }

    public static String requireUsername() {
        return currentUsername().orElseThrow(() ->
                new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                        "Authentication required"));
    }

    public static boolean hasAuthority(String authority) {
        return authorities().contains(authority);
    }

    public static Set<String> authorities() {
        return currentAuthentication()
                .map(a -> a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()))
                .orElse(Set.of());
    }
}

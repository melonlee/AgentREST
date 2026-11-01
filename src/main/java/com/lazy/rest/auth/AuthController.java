package com.lazy.rest.auth;

import com.lazy.rest.auth.AuthDtos.LoginRequest;
import com.lazy.rest.auth.AuthDtos.MeResponse;
import com.lazy.rest.auth.AuthDtos.RefreshRequest;
import com.lazy.rest.auth.AuthDtos.RegisterRequest;
import com.lazy.rest.auth.AuthDtos.TokenResponse;
import com.lazy.rest.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Login, token refresh/rotation, logout, registration and current user")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in with username + password", description = "Rate limited per client IP (429 + Retry-After).")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.acquire("login:" + http.getRemoteAddr());
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new token pair (the old refresh token is revoked)")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        rateLimiter.acquire("refresh:" + http.getRemoteAddr());
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/register")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Self-register as ROLE_USER", description = "Only when lazyrest.auth.registration-enabled=true (default in dev).")
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.acquire("register:" + http.getRemoteAddr());
        return authService.register(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user, roles and permissions")
    public MeResponse me() {
        return authService.me();
    }
}

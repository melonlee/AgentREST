package com.fantasyailab.agentrest.auth;

import com.fantasyailab.agentrest.audit.Audited;
import com.fantasyailab.agentrest.auth.AuthDtos.LoginRequest;
import com.fantasyailab.agentrest.auth.AuthDtos.MeResponse;
import com.fantasyailab.agentrest.auth.AuthDtos.RegisterRequest;
import com.fantasyailab.agentrest.auth.AuthDtos.TokenResponse;
import com.fantasyailab.agentrest.auth.apikey.ApiKeyAuthenticationToken;
import com.fantasyailab.agentrest.common.error.ApiException;
import com.fantasyailab.agentrest.common.error.ResourceNotFoundException;
import com.fantasyailab.agentrest.common.security.SecureTokens;
import com.fantasyailab.agentrest.common.security.SecurityUtils;
import com.fantasyailab.agentrest.user.Role;
import com.fantasyailab.agentrest.user.User;
import com.fantasyailab.agentrest.user.UserDtos.UserResponse;
import com.fantasyailab.agentrest.user.UserRepository;
import com.fantasyailab.agentrest.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final UserService userService;
    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokens;
    private final AuthProperties properties;

    public AuthService(AuthenticationManager authenticationManager, UserRepository users, UserService userService,
            TokenService tokenService, RefreshTokenRepository refreshTokens, AuthProperties properties) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.userService = userService;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
    }

    @Transactional
    @Audited(action = "AUTH_LOGIN", resourceType = "user", actor = "#request.username()")
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        User user = users.findWithAuthoritiesByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        return tokenService.issue(user);
    }

    /**
     * Rotates the refresh token. Presenting an already-revoked token is treated as theft
     * (token reuse): every refresh token of that user is revoked.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    @Audited(action = "AUTH_REFRESH", resourceType = "user")
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        User user = token.getUser();
        if (token.getRevokedAt() != null) {
            int revoked = refreshTokens.revokeAllForUser(user.getId(), now);
            log.warn("Refresh token reuse detected for user '{}'; revoked {} active token(s)", user.getUsername(), revoked);
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!token.isActive(now) || !user.isEnabled()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        token.revoke(now);
        User withAuthorities = users.findWithAuthoritiesById(user.getId()).orElseThrow();
        return tokenService.issue(withAuthorities);
    }

    @Transactional
    @Audited(action = "AUTH_LOGOUT", resourceType = "user")
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional
    @Audited(action = "AUTH_REGISTER", resourceType = "user", actor = "#request.username()", resourceId = "#result.id")
    public UserResponse register(RegisterRequest request) {
        if (!properties.registrationEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_DISABLED", "Self-registration is disabled");
        }
        User user = userService.createUser(request.username(), request.email(), request.password(),
                request.displayName(), Set.of(Role.USER));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        String username = SecurityUtils.requireUsername();
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User", username));
        Set<String> roles = new TreeSet<>();
        Set<String> permissions = new TreeSet<>();
        for (String authority : SecurityUtils.authorities()) {
            if (authority.startsWith("FACTOR_")) {
                continue; // Spring Security 7 authentication-factor markers, not permissions
            }
            (authority.startsWith("ROLE_") ? roles : permissions).add(authority);
        }
        String method = SecurityUtils.currentAuthentication().orElseThrow() instanceof ApiKeyAuthenticationToken
                ? "api-key" : "jwt";
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), roles,
                permissions, method);
    }
}

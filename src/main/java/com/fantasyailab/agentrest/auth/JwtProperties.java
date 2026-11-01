package com.fantasyailab.agentrest.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings. The application refuses to start when the secret is missing or shorter than
 * 32 bytes (HS256 requires a 256-bit key), so a misconfigured prod deployment fails fast.
 */
@Validated
@ConfigurationProperties("agentrest.security.jwt")
public record JwtProperties(
        @NotBlank(message = "must be set - provide the JWT_SECRET environment variable (>= 32 bytes)") String secret,
        @NotBlank @DefaultValue("agentrest") String issuer,
        @NotNull @DefaultValue("15m") Duration accessTokenTtl,
        @NotNull @DefaultValue("14d") Duration refreshTokenTtl) {

    public JwtProperties {
        if (secret != null && !secret.isBlank() && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "agentrest.security.jwt.secret (JWT_SECRET) must be at least 32 bytes; generate one with `openssl rand -base64 48`");
        }
    }

    public boolean isDevSecret() {
        return secret != null && secret.startsWith("dev-only-");
    }
}

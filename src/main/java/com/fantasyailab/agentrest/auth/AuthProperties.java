package com.fantasyailab.agentrest.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("agentrest.auth")
public record AuthProperties(
        @DefaultValue("false") boolean registrationEnabled,
        @Valid @NotNull @DefaultValue LoginRateLimit loginRateLimit,
        @DefaultValue BootstrapAdmin bootstrapAdmin) {

    public record LoginRateLimit(@Min(1) @DefaultValue("10") int maxAttempts,
                                 @NotNull @DefaultValue("1m") Duration window) {
    }

    public record BootstrapAdmin(String username, String email, String password) {

        public boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank()
                    && email != null && !email.isBlank();
        }
    }
}

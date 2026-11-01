package com.lazy.rest.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

/** The application must refuse to start with a missing, short or dev-only secret in prod. */
class JwtConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class, JwtConfig.class)
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    @Test
    void missingSecretFailsFastWithAHelpfulMessage() {
        runner.withPropertyValues("lazyrest.security.jwt.secret=")
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void shortSecretIsRejected() {
        runner.withPropertyValues("lazyrest.security.jwt.secret=too-short")
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("at least 32 bytes"));
    }

    @Test
    void devSecretIsRejectedWithTheProdProfile() {
        runner.withPropertyValues("lazyrest.security.jwt.secret=dev-only-insecure-jwt-secret-never-use-in-production-0123456789",
                        "spring.profiles.active=prod")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("must not be used with the prod profile"));
    }

    @Test
    void strongSecretStarts() {
        runner.withPropertyValues("lazyrest.security.jwt.secret=8f3a1c9e5b7d2f4a6c8e0b1d3f5a7c9e2b4d6f8a0c")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class));
    }
}

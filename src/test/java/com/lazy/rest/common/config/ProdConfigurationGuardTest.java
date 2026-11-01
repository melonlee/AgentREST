package com.lazy.rest.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProdConfigurationGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProdConfigurationGuard.class);

    @Test
    void prodWithoutEnvironmentFailsListingEveryMissingVariable() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("DB_URL, DB_USERNAME, JWT_SECRET, DB_PASSWORD"));
    }

    @Test
    void prodWithCompleteEnvironmentStarts() {
        runner.withPropertyValues("spring.profiles.active=prod", "DB_URL=jdbc:mysql://db/lazyrest",
                        "DB_USERNAME=app", "DB_PASSWORD=", "JWT_SECRET=0123456789abcdef0123456789abcdef")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void otherProfilesAreNotAffected() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}

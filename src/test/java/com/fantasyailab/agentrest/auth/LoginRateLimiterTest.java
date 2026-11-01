package com.fantasyailab.agentrest.auth;

import com.fantasyailab.agentrest.common.error.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToTheLimitThenRejectsWithRetryAfter() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMinutes(1), clock);

        limiter.acquire("ip-1");
        clock.now = clock.now.plusSeconds(20);
        limiter.acquire("ip-1");

        assertThatThrownBy(() -> limiter.acquire("ip-1"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(40)));
    }

    @Test
    void keysAreIndependentAndWindowsReset() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(1), clock);

        limiter.acquire("ip-1");
        assertThatCode(() -> limiter.acquire("ip-2")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquire("ip-1")).isInstanceOf(RateLimitExceededException.class);

        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.acquire("ip-1")).doesNotThrowAnyException();
    }
}

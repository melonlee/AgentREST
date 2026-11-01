package com.fantasyailab.agentrest.auth;

import com.fantasyailab.agentrest.common.error.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, in-memory rate limiter for credential endpoints (login / register / refresh),
 * keyed by client IP. Deliberately simple: it is per instance, so behind N replicas the effective
 * limit is N x maxAttempts. Swap for Bucket4j + Redis if you need a cluster-wide limit.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimiter(AuthProperties properties) {
        this(properties.loginRateLimit().maxAttempts(), properties.loginRateLimit().window(), Clock.systemUTC());
    }

    LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Counts an attempt for {@code key}; throws {@link RateLimitExceededException} once the limit is exceeded. */
    public void acquire(String key) {
        Instant now = clock.instant();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> w.isExpired(now, window));
        }
        Window current = windows.compute(key, (k, w) ->
                (w == null || w.isExpired(now, window)) ? new Window(now, 1) : new Window(w.start(), w.count() + 1));
        if (current.count() > maxAttempts) {
            Duration retryAfter = Duration.between(now, current.start().plus(window));
            throw new RateLimitExceededException(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        }
    }

    private record Window(Instant start, int count) {
        boolean isExpired(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}

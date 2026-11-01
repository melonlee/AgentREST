package com.fantasyailab.agentrest.common.error;

import org.springframework.http.HttpStatus;

import java.time.Duration;

public class RateLimitExceededException extends ApiException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many attempts. Try again later.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}

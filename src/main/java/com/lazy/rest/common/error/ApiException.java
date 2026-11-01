package com.lazy.rest.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected, client-facing errors. The message is returned to the client as the
 * ProblemDetail {@code detail}, so it must never contain secrets or internals.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

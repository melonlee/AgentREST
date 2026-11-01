package com.fantasyailab.agentrest.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns every error into an RFC 9457 {@code application/problem+json} response with a stable
 * {@code code}, the {@code requestId} and, for validation failures, a list of field {@code errors}.
 * Unexpected exceptions are logged with the request ID and never leak internals to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = Problems.of(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex instanceof RateLimitExceededException rate) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, rate.getRetryAfter().toSeconds())));
        }
        return builder.body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", fieldName(v), "message", v.getMessage()))
                .toList();
        return validationProblem(errors, request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String detail = ex.getMessage() != null && ex.getMessage().startsWith("Invalid")
                ? ex.getMessage() : "Invalid username or password";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", detail, request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action", request.getRequestURI()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.of(HttpStatus.CONFLICT, "CONFLICT",
                "The resource was modified concurrently. Reload and retry.", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.debug("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.of(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with existing data", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the requestId when reporting it.", request.getRequestURI()));
    }

    // ---- Spring MVC built-in exceptions -------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())));
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                errors.add(Map.of("field", ge.getObjectName(), "message", String.valueOf(ge.getDefaultMessage()))));
        return ResponseEntity.badRequest().body(validationProblem(errors, path(request)).getBody());
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(err ->
                errors.add(Map.of("field", String.valueOf(result.getMethodParameter().getParameterName()),
                        "message", String.valueOf(err.getDefaultMessage())))));
        return ResponseEntity.badRequest().body(validationProblem(errors, path(request)).getBody());
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.enrich(problem, codeFor(statusCode), path(request));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static ResponseEntity<ProblemDetail> validationProblem(List<Map<String, String>> errors, String instance) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", instance);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    static String fieldName(ConstraintViolation<?> violation) {
        List<String> props = new ArrayList<>();
        String parameter = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PARAMETER) {
                parameter = node.getName();
            } else if (node.getKind() == ElementKind.PROPERTY) {
                props.add(node.getName());
            }
        }
        if (!props.isEmpty()) {
            return String.join(".", props);
        }
        return parameter != null ? parameter : violation.getPropertyPath().toString();
    }

    private static String codeFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.name() : "HTTP_" + status.value();
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }
}

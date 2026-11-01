package com.lazy.rest.common.error;

import com.lazy.rest.common.web.RequestIdFilter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds RFC 9457 problem details with the extension members every LazyREST error carries:
 * {@code code} (stable machine-readable error code), {@code requestId} and {@code timestamp}.
 */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return enrich(problem, code, instance);
    }

    public static ProblemDetail enrich(ProblemDetail problem, String code, String instance) {
        if (problem.getInstance() == null && instance != null) {
            problem.setInstance(URI.create(instance));
        }
        Map<String, Object> props = problem.getProperties();
        if (props == null || !props.containsKey("code")) {
            problem.setProperty("code", code);
        }
        problem.setProperty("requestId", RequestIdFilter.currentRequestId());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    /** Flattens a ProblemDetail into a JSON-ready map (used where no MVC message converter is available). */
    public static Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (problem.getType() != null) {
            body.put("type", problem.getType().toString());
        }
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        if (problem.getDetail() != null) {
            body.put("detail", problem.getDetail());
        }
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}

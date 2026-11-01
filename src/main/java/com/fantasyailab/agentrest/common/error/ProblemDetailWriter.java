package com.fantasyailab.agentrest.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** Writes problem+json from servlet filters (security entry points), where MVC advice does not apply. */
@Component
public class ProblemDetailWriter {

    private final JsonMapper jsonMapper;

    public ProblemDetailWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatusCode status, String code,
            String detail) throws IOException {
        ProblemDetail problem = Problems.of(status, code, detail, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), Problems.toMap(problem));
    }
}

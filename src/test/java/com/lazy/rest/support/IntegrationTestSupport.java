package com.lazy.rest.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Full-stack tests: the real application on a random port (dev profile: H2 + demo data), called
 * over HTTP. All subclasses share one cached Spring context, so tests must not depend on global
 * counts - create your own data and assert on it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lazyrest.auth.login-rate-limit.max-attempts=100000")
public abstract class IntegrationTestSupport {

    public static final String EDITOR_API_KEY = "lr_dev_editor_0123456789abcdefghijklmnop";

    @LocalServerPort
    protected int port;

    @Autowired
    protected JsonMapper jsonMapper;

    protected RestClient http;

    @BeforeEach
    void setUpHttpClient() {
        http = RestClient.builder().baseUrl(baseUrl()).build();
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected Response get(String path, String token, Object... uriVariables) {
        return exchange(HttpMethod.GET, path, token, null, h -> { }, uriVariables);
    }

    protected Response post(String path, String token, Object body) {
        return exchange(HttpMethod.POST, path, token, body, h -> { });
    }

    protected Response put(String path, String token, Object body) {
        return exchange(HttpMethod.PUT, path, token, body, h -> { });
    }

    protected Response delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, token, null, h -> { });
    }

    protected Response exchange(HttpMethod method, String path, String token, Object body,
            Consumer<HttpHeaders> headers, Object... uriVariables) {
        RestClient.RequestBodySpec spec = http.method(method).uri(path, uriVariables).headers(h -> {
            if (token != null) {
                h.setBearerAuth(token);
            }
            headers.accept(h);
        });
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
        }
        return spec.exchange((request, response) -> new Response(response.getStatusCode().value(),
                response.getHeaders(), new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    protected TokenPair loginPair(String username, String password) {
        Response response = post("/api/auth/login", null, Map.of("username", username, "password", password));
        if (response.status() != 200) {
            throw new IllegalStateException("Login failed for " + username + ": " + response);
        }
        return new TokenPair(response.str("$.accessToken"), response.str("$.refreshToken"));
    }

    protected String login(String username, String password) {
        return loginPair(username, password).accessToken();
    }

    protected String adminToken() {
        return login("admin", "admin123");
    }

    protected String editorToken() {
        return login("editor", "editor123");
    }

    protected String aliceToken() {
        return login("alice", "alice123");
    }

    protected String bobToken() {
        return login("bob", "bob123456");
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }

    public record Response(int status, HttpHeaders headers, String body) {

        public <T> T json(String path) {
            return JsonPath.read(body, path);
        }

        public String str(String path) {
            Object value = json(path);
            return value == null ? null : value.toString();
        }

        public long id() {
            return ((Number) json("$.id")).longValue();
        }

        public String contentType() {
            return headers.getFirst(HttpHeaders.CONTENT_TYPE);
        }
    }
}

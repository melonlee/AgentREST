package com.lazy.rest.user;

import com.lazy.rest.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RbacTests extends IntegrationTestSupport {

    @Test
    void regularUserCannotManageUsers() {
        Response response = get("/api/users", aliceToken());

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.str("$.code")).isEqualTo("FORBIDDEN");
    }

    @Test
    void regularUserCannotReadTheAuditLog() {
        assertThat(get("/api/audit-logs", aliceToken()).status()).isEqualTo(403);
        assertThat(get("/api/audit-logs", editorToken()).status()).isEqualTo(403);
    }

    @Test
    void adminCanListCreateAndUpdateUsers() {
        String admin = adminToken();
        assertThat(get("/api/users?q=ali", admin).<List<String>>json("$.content[*].username")).contains("alice");

        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        Response created = post("/api/users", admin, Map.of("username", username, "email", username + "@example.com",
                "password", "password-123", "roles", Set.of("ROLE_EDITOR")));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.headers().getLocation()).hasPath("/api/users/" + created.id());

        Response updated = exchange(org.springframework.http.HttpMethod.PATCH, "/api/users/" + created.id(), admin,
                Map.of("enabled", false), h -> { });
        assertThat(updated.status()).isEqualTo(200);
        assertThat((Boolean) updated.json("$.enabled")).isFalse();

        // Disabled users can no longer log in
        Response login = post("/api/auth/login", null, Map.of("username", username, "password", "password-123"));
        assertThat(login.status()).isEqualTo(401);
    }

    @Test
    void unknownRoleIsABadRequest() {
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        Response response = post("/api/users", adminToken(), Map.of("username", username,
                "email", username + "@example.com", "password", "password-123", "roles", Set.of("ROLE_GOD")));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.str("$.detail")).contains("Unknown role");
    }

    @Test
    void actuatorIsAdminOnlyExceptHealth() {
        assertThat(get("/actuator/health", null).status()).isEqualTo(200);
        assertThat(get("/actuator/info", null).status()).isEqualTo(401);
        assertThat(get("/actuator/metrics", aliceToken()).status()).isEqualTo(403);
        assertThat(get("/actuator/metrics", adminToken()).status()).isEqualTo(200);
        assertThat(get("/actuator/prometheus", adminToken()).body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void swaggerUiAndOpenApiArePublic() {
        Response docs = get("/v3/api-docs", null);
        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.str("$.components.securitySchemes.bearerAuth.scheme")).isEqualTo("bearer");
        assertThat(docs.<Map<String, Object>>json("$.paths")).containsKeys("/api/articles", "/api/auth/login");
        assertThat(get("/swagger-ui/index.html", null).status()).isEqualTo(200);
    }

    @Test
    void unmappedPathsOutsideTheApiAreDenied() {
        assertThat(get("/internal/secret", adminToken()).status()).isEqualTo(403);
    }
}

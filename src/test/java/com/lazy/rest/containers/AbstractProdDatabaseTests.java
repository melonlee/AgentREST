package com.lazy.rest.containers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.JdbcDatabaseContainer;

import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the app with the real {@code prod} profile against a real database (Testcontainers):
 * Flyway vendor migrations, Hibernate schema validation, env-var based configuration, the
 * bootstrap admin and an end-to-end REST round trip. Requires Docker - runs in CI via
 * {@code ./mvnw -Ptestcontainers test}.
 */
@Tag("docker")
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractProdDatabaseTests {

    static final String ADMIN_PASSWORD = "container-admin-password";

    @LocalServerPort
    int port;

    static void registerProdEnvironment(DynamicPropertyRegistry registry, JdbcDatabaseContainer<?> db) {
        registry.add("DB_URL", db::getJdbcUrl);
        registry.add("DB_USERNAME", db::getUsername);
        registry.add("DB_PASSWORD", db::getPassword);
        registry.add("JWT_SECRET", () -> "testcontainers-secret-0123456789abcdef0123456789");
        registry.add("LAZYREST_ADMIN_USERNAME", () -> "root");
        registry.add("LAZYREST_ADMIN_EMAIL", () -> "root@example.com");
        registry.add("LAZYREST_ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
    }

    @Test
    void bootstrapAdminCanLogInCreateAndSearchArticles() {
        RestClient http = RestClient.create("http://localhost:" + port);

        String login = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"root\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .retrieve().body(String.class);
        String token = JsonPath.read(login, "$.accessToken");

        String created = http.post().uri("/api/articles").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body("{\"title\":\"Hello 100% real DB\",\"content\":\"" + "long body ".repeat(1500) + "\"}")
                .retrieve().body(String.class);
        Number id = JsonPath.read(created, "$.id");

        String published = http.post().uri("/api/articles/{id}/publish", id)
                .header("Authorization", "Bearer " + token).retrieve().body(String.class);
        assertThat((String) JsonPath.read(published, "$.status")).isEqualTo("PUBLISHED");

        String search = http.get().uri("/api/articles?q={q}&sort=publishedAt,desc", "100%")
                .header("Authorization", "Bearer " + token).retrieve().body(String.class);
        assertThat(JsonPath.<List<String>>read(search, "$.content[*].title")).containsExactly("Hello 100% real DB");

        String audit = http.get().uri("/api/audit-logs?action=ARTICLE_PUBLISH")
                .header("Authorization", "Bearer " + token).retrieve().body(String.class);
        assertThat(JsonPath.<List<String>>read(audit, "$.content[*].resourceId")).contains(id.toString());
    }

    @Test
    void devDataIsNotSeededInProd() {
        RestClient http = RestClient.create("http://localhost:" + port);
        int status = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"admin\",\"password\":\"admin123\"}")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(401);
    }
}

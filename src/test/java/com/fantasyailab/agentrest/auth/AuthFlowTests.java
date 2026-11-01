package com.fantasyailab.agentrest.auth;

import com.fantasyailab.agentrest.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowTests extends IntegrationTestSupport {

    @Test
    void loginReturnsTokenPairAndAccessTokenWorks() {
        Response login = post("/api/auth/login", null, Map.of("username", "editor", "password", "editor123"));

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.str("$.tokenType")).isEqualTo("Bearer");
        assertThat((Integer) login.json("$.expiresIn")).isEqualTo(900);
        assertThat(login.str("$.refreshToken")).hasSize(43);

        Response me = get("/api/auth/me", login.str("$.accessToken"));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.str("$.username")).isEqualTo("editor");
        assertThat(me.str("$.authMethod")).isEqualTo("jwt");
        assertThat(me.<List<String>>json("$.roles")).containsExactly("ROLE_EDITOR");
        assertThat(me.<List<String>>json("$.permissions"))
                .containsExactlyInAnyOrder("article:read", "article:write", "article:publish", "mcp:write");
    }

    @Test
    void wrongPasswordIsRejectedWithProblemDetail() {
        Response response = post("/api/auth/login", null, Map.of("username", "admin", "password", "wrong-password"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.str("$.code")).isEqualTo("UNAUTHORIZED");
        assertThat(response.str("$.detail")).isEqualTo("Invalid username or password");
        assertThat(response.str("$.requestId")).isEqualTo(response.headers().getFirst("X-Request-Id"));
    }

    @Test
    void unknownUserGetsTheSameAnswerAsWrongPassword() {
        Response response = post("/api/auth/login", null, Map.of("username", "nobody", "password", "whatever1"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.str("$.detail")).isEqualTo("Invalid username or password");
    }

    @Test
    void missingTokenIs401WithWwwAuthenticate() {
        Response response = get("/api/articles", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.str("$.instance")).isEqualTo("/api/articles");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = editorToken();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        Response response = get("/api/auth/me", tampered);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst("WWW-Authenticate")).contains("invalid_token");
        assertThat(response.str("$.detail")).isEqualTo("Invalid or expired access token");
    }

    @Test
    void apiKeysAreNotAcceptedOnTheRestApi() {
        Response response = get("/api/auth/me", EDITOR_API_KEY);

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void refreshRotatesTokensAndDetectsReuse() {
        TokenPair first = loginPair("alice", "alice123");

        Response refreshed = post("/api/auth/refresh", null, Map.of("refreshToken", first.refreshToken()));
        assertThat(refreshed.status()).isEqualTo(200);
        String secondRefresh = refreshed.str("$.refreshToken");
        assertThat(secondRefresh).isNotEqualTo(first.refreshToken());
        assertThat(get("/api/auth/me", refreshed.str("$.accessToken")).status()).isEqualTo(200);

        // Re-using the rotated token is treated as theft ...
        Response reuse = post("/api/auth/refresh", null, Map.of("refreshToken", first.refreshToken()));
        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.str("$.detail")).isEqualTo("Invalid refresh token");

        // ... and revokes the whole family, including the newest token.
        Response afterReuse = post("/api/auth/refresh", null, Map.of("refreshToken", secondRefresh));
        assertThat(afterReuse.status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        TokenPair pair = loginPair("bob", "bob123456");

        assertThat(post("/api/auth/logout", null, Map.of("refreshToken", pair.refreshToken())).status()).isEqualTo(204);
        assertThat(post("/api/auth/refresh", null, Map.of("refreshToken", pair.refreshToken())).status()).isEqualTo(401);
    }

    @Test
    void registrationCreatesAUserRoleAccount() {
        String username = "reg" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> body = Map.of("username", username, "email", username + "@example.com",
                "password", "s3cure-pass", "displayName", "Reg");

        Response created = post("/api/auth/register", null, body);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.<List<String>>json("$.roles")).containsExactly("ROLE_USER");

        Response duplicate = post("/api/auth/register", null, body);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.str("$.code")).isEqualTo("CONFLICT");

        assertThat(login(username, "s3cure-pass")).isNotBlank();
    }

    @Test
    void requestIdIsEchoedWhenWellFormedAndReplacedOtherwise() {
        Response echoed = exchange(HttpMethod.GET, "/actuator/health", null, null,
                h -> h.set("X-Request-Id", "trace-123"));
        assertThat(echoed.headers().getFirst("X-Request-Id")).isEqualTo("trace-123");

        Response replaced = exchange(HttpMethod.GET, "/actuator/health", null, null,
                h -> h.set("X-Request-Id", "bad id with spaces"));
        assertThat(replaced.headers().getFirst("X-Request-Id")).isNotEqualTo("bad id with spaces").hasSize(36);
    }
}

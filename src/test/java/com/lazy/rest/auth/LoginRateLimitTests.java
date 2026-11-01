package com.lazy.rest.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"lazyrest.auth.login-rate-limit.max-attempts=3", "lazyrest.auth.login-rate-limit.window=1h"})
class LoginRateLimitTests {

    @LocalServerPort
    int port;

    @Test
    void fourthAttemptWithinTheWindowGets429WithRetryAfter() {
        RestClient http = RestClient.create("http://localhost:" + port);
        String body = "{\"username\":\"admin\",\"password\":\"wrong-password\"}";

        int[] statuses = new int[4];
        String retryAfter = null;
        String problem = null;
        for (int i = 0; i < 4; i++) {
            var result = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).body(body)
                    .exchange((req, res) -> new String[] {String.valueOf(res.getStatusCode().value()),
                            res.getHeaders().getFirst("Retry-After"), new String(res.getBody().readAllBytes())});
            statuses[i] = Integer.parseInt(result[0]);
            retryAfter = result[1];
            problem = result[2];
        }

        assertThat(statuses).containsExactly(401, 401, 401, 429);
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 3600L);
        assertThat(problem).contains("\"code\":\"RATE_LIMITED\"");
    }
}

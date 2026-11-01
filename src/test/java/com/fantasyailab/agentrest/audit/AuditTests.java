package com.fantasyailab.agentrest.audit;

import com.fantasyailab.agentrest.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTests extends IntegrationTestSupport {

    @Test
    void successfulWritesAreAuditedWithActorResourceAndRequestId() {
        Response created = post("/api/articles", aliceToken(),
                Map.of("title", "Audited " + UUID.randomUUID(), "content", "body"));
        String requestId = created.headers().getFirst("X-Request-Id");

        Response logs = get("/api/audit-logs?action=ARTICLE_CREATE&actor=alice&size=100", adminToken());

        assertThat(logs.status()).isEqualTo(200);
        List<Map<String, Object>> entries = logs.json("$.content[?(@.requestId == '" + requestId + "')]");
        assertThat(entries).hasSize(1);
        Map<String, Object> entry = entries.getFirst();
        assertThat(entry).containsEntry("resourceType", "article")
                .containsEntry("resourceId", String.valueOf(created.id()))
                .containsEntry("channel", "REST")
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("clientIp", "127.0.0.1");
    }

    @Test
    void deniedOperationsAreAuditedAsDenied() {
        String alice = aliceToken();
        long id = post("/api/articles", alice, Map.of("title", "Deny me " + UUID.randomUUID(), "content", "b")).id();
        Response denied = post("/api/articles/" + id + "/publish", alice, null);
        assertThat(denied.status()).isEqualTo(403);

        Response logs = get("/api/audit-logs?action=ARTICLE_PUBLISH&outcome=DENIED&actor=alice", adminToken());

        assertThat(logs.<List<String>>json("$.content[*].requestId"))
                .contains(denied.headers().getFirst("X-Request-Id"));
    }

    @Test
    void failedLoginsAreAudited() {
        String user = "ghost" + UUID.randomUUID().toString().substring(0, 6);
        post("/api/auth/login", null, Map.of("username", user, "password", "nope-nope"));

        Response logs = get("/api/audit-logs?action=AUTH_LOGIN&actor=" + user, adminToken());

        assertThat(logs.<List<String>>json("$.content[*].outcome")).containsExactly("DENIED");
    }

    @Test
    void auditLogIsNewestFirstAndFilterableByChannel() {
        Response logs = get("/api/audit-logs?channel=REST&size=5", adminToken());

        assertThat(logs.<List<String>>json("$.content[*].channel")).allMatch("REST"::equals);
        assertThat(logs.<List<String>>json("$.sort")).containsExactly("occurredAt,desc", "id,desc");
    }
}

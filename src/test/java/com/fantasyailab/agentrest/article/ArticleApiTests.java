package com.fantasyailab.agentrest.article;

import com.fantasyailab.agentrest.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleApiTests extends IntegrationTestSupport {

    private static Map<String, String> article(String title) {
        return Map.of("title", title, "summary", "summary of " + title, "content", "Body of " + title);
    }

    @Test
    void crudLifecycleForTheAuthor() {
        String alice = aliceToken();

        Response created = post("/api/articles", alice, article("Alice CRUD " + UUID.randomUUID()));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.str("$.status")).isEqualTo("DRAFT");
        assertThat(created.str("$.author")).isEqualTo("alice");
        long id = created.id();
        assertThat(created.headers().getLocation()).hasPath("/api/articles/" + id);

        assertThat(get("/api/articles/" + id, alice).str("$.title")).startsWith("Alice CRUD");

        Response updated = put("/api/articles/" + id, alice, Map.of("title", "Renamed", "content", "New body"));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.str("$.title")).isEqualTo("Renamed");
        assertThat(updated.str("$.summary")).isNull();
        assertThat((Integer) updated.json("$.version")).isEqualTo(1);

        assertThat(delete("/api/articles/" + id, alice).status()).isEqualTo(204);
        assertThat(get("/api/articles/" + id, alice).status()).isEqualTo(404);
    }

    @Test
    void draftsAreInvisibleToOtherUsersAndReportedAs404() {
        long id = post("/api/articles", aliceToken(), article("Secret draft " + UUID.randomUUID())).id();

        Response asBob = get("/api/articles/" + id, bobToken());
        assertThat(asBob.status()).isEqualTo(404);
        assertThat(asBob.str("$.code")).isEqualTo("NOT_FOUND");

        // admins (article:manage) can see everything
        assertThat(get("/api/articles/" + id, adminToken()).status()).isEqualTo(200);
    }

    @Test
    void onlyTheAuthorOrAManagerMayModifyAPublishedArticle() {
        String editor = editorToken();
        long id = post("/api/articles", editor, article("Editor article " + UUID.randomUUID())).id();
        assertThat(post("/api/articles/" + id + "/publish", editor, null).str("$.status")).isEqualTo("PUBLISHED");

        Response bobUpdate = put("/api/articles/" + id, bobToken(), article("Hijacked"));
        assertThat(bobUpdate.status()).isEqualTo(403);
        assertThat(delete("/api/articles/" + id, bobToken()).status()).isEqualTo(403);

        Response adminUpdate = put("/api/articles/" + id, adminToken(), article("Moderated title"));
        assertThat(adminUpdate.status()).isEqualTo(200);
    }

    @Test
    void usersWithoutPublishPermissionCannotPublish() {
        String alice = aliceToken();
        long id = post("/api/articles", alice, article("Alice wants to publish " + UUID.randomUUID())).id();

        Response response = post("/api/articles/" + id + "/publish", alice, null);

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void validationErrorsAreReportedPerField() {
        Response response = post("/api/articles", aliceToken(), Map.of("title", "", "content", "x".repeat(20_001)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.str("$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.<List<String>>json("$.errors[*].field")).containsExactlyInAnyOrder("title", "content");
        assertThat(response.<List<String>>json("$.errors[*].message"))
                .contains("must not be blank", "size must be between 0 and 20000");
    }

    @Test
    void malformedJsonIsABadRequestProblem() {
        Response response = exchange(org.springframework.http.HttpMethod.POST, "/api/articles", aliceToken(), null,
                h -> h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.contentType()).startsWith("application/problem+json");
    }

    @Test
    void searchPaginatesAndSortsWithinTheCallersVisibility() {
        String editor = editorToken();
        String tag = "pg" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= 5; i++) {
            post("/api/articles", editor, article(tag + " item " + i));
        }

        Response page0 = get("/api/articles?q=" + tag + "&size=2&page=0&sort=title,asc", editor);
        assertThat(page0.status()).isEqualTo(200);
        assertThat((Integer) page0.json("$.totalElements")).isEqualTo(5);
        assertThat((Integer) page0.json("$.totalPages")).isEqualTo(3);
        assertThat((Integer) page0.json("$.size")).isEqualTo(2);
        assertThat(page0.<List<String>>json("$.content[*].title")).containsExactly(tag + " item 1", tag + " item 2");
        assertThat(page0.<List<String>>json("$.sort")).containsExactly("title,asc");

        Response page2 = get("/api/articles?q=" + tag + "&size=2&page=2&sort=title,asc", editor);
        assertThat(page2.<List<String>>json("$.content[*].title")).containsExactly(tag + " item 5");

        // The drafts belong to editor: other readers do not find them
        assertThat((Integer) get("/api/articles?q=" + tag, aliceToken()).json("$.totalElements")).isZero();
    }

    @Test
    void searchTreatsLikeWildcardsLiterally() {
        String editor = editorToken();
        String tag = "wc" + UUID.randomUUID().toString().substring(0, 6);
        post("/api/articles", editor, article(tag + " 100% done"));
        post("/api/articles", editor, article(tag + " 100 percent"));

        Response response = get("/api/articles?q={q}", editor, tag + " 100%");

        assertThat(response.<List<String>>json("$.content[*].title")).containsExactly(tag + " 100% done");
    }

    @Test
    void filtersByStatusAndAuthor() {
        Response published = get("/api/articles?status=PUBLISHED&author=editor&size=100", aliceToken());

        assertThat(published.<List<String>>json("$.content[*].status")).isNotEmpty().allMatch("PUBLISHED"::equals);
        assertThat(published.<List<String>>json("$.content[*].author")).allMatch("editor"::equals);
    }

    @Test
    void unsupportedSortPropertyAndOversizedPagesAreHandled() {
        Response badSort = get("/api/articles?sort=author.passwordHash", aliceToken());
        assertThat(badSort.status()).isEqualTo(400);
        assertThat(badSort.str("$.detail")).contains("Unsupported sort property");

        Response huge = get("/api/articles?size=5000", aliceToken());
        assertThat((Integer) huge.json("$.size")).isEqualTo(100);
    }
}

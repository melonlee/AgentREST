package com.lazy.rest.article;

import com.lazy.rest.common.api.LikePatterns;
import com.lazy.rest.user.Role;
import com.lazy.rest.user.RoleRepository;
import com.lazy.rest.user.User;
import com.lazy.rest.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice on the dev H2 database (MySQL mode) with the real Flyway migrations, so this also
 * proves the migrations and the entity mappings agree ({@code ddl-auto=validate}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceTest {

    @Autowired
    ArticleRepository articles;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    EntityManager entityManager;

    private User author;

    @BeforeEach
    void setUp() {
        author = new User("writer", "writer@example.com", "{noop}x", "Writer");
        author.getRoles().add(roles.findByName(Role.USER).orElseThrow());
        users.save(author);
    }

    @Test
    void referenceDataIsSeededByFlyway() {
        assertThat(roles.findAll()).extracting(Role::getName)
                .containsExactlyInAnyOrder(Role.ADMIN, Role.EDITOR, Role.USER);
        assertThat(roles.findByName(Role.EDITOR).orElseThrow().getPermissions())
                .extracting(p -> p.getName())
                .containsExactlyInAnyOrder("article:read", "article:write", "article:publish", "mcp:write");
        assertThat(roles.findByName(Role.ADMIN).orElseThrow().getPermissions()).hasSize(7);
    }

    @Test
    void authoritiesCombineRolesAndPermissions() {
        entityManager.flush();
        entityManager.clear();

        User loaded = users.findWithAuthoritiesByUsername("writer").orElseThrow();

        assertThat(loaded.authorities()).containsExactlyInAnyOrder(Role.USER, "article:read", "article:write");
    }

    @Test
    void auditingTimestampsAndOptimisticVersionAreMaintained() {
        Article article = articles.saveAndFlush(new Article("Title", null, "Body", author));
        assertThat(article.getCreatedAt()).isNotNull();
        assertThat(article.getVersion()).isZero();

        article.edit("New title", null, "Body");
        articles.saveAndFlush(article);

        assertThat(article.getVersion()).isEqualTo(1);
        assertThat(article.getUpdatedAt()).isAfterOrEqualTo(article.getCreatedAt());
    }

    @Test
    void specificationSearchWithEscapedLikeAndEntityGraph() {
        articles.save(new Article("Spring 100% guide", "x", "b", author));
        articles.save(new Article("Spring 100 tips", "x", "b", author));
        Article published = new Article("Published one", "about mcp", "b", author);
        published.publish();
        articles.save(published);
        entityManager.flush();
        entityManager.clear();

        String pattern = LikePatterns.contains("100%");
        Specification<Article> titleLike = (root, q, cb) ->
                cb.like(cb.lower(root.get("title")), pattern, LikePatterns.ESCAPE);
        var page = articles.findAll(titleLike, PageRequest.of(0, 10, Sort.by("title")));

        assertThat(page.getContent()).extracting(Article::getTitle).containsExactly("Spring 100% guide");
        // author is fetched by the entity graph (no lazy-loading outside a session needed)
        assertThat(page.getContent().getFirst().getAuthor().getUsername()).isEqualTo("writer");

        Specification<Article> publishedOnly = (root, q, cb) -> cb.equal(root.get("status"), Article.Status.PUBLISHED);
        assertThat(articles.findAll(publishedOnly, PageRequest.of(0, 10)).getContent())
                .extracting(Article::getTitle).containsExactly("Published one");
    }

    @Test
    void userSearchUsesEscapedPatterns() {
        assertThat(users.searchByPattern(LikePatterns.contains("WRIT"), PageRequest.of(0, 10)).getContent())
                .extracting(User::getUsername).containsExactly("writer");
        assertThat(users.searchByPattern(LikePatterns.contains("wr_ter"), PageRequest.of(0, 10)).getContent()).isEmpty();
    }
}

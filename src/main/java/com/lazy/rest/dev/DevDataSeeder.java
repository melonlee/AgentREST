package com.lazy.rest.dev;

import com.lazy.rest.article.Article;
import com.lazy.rest.article.ArticleRepository;
import com.lazy.rest.auth.apikey.ApiKeyService;
import com.lazy.rest.user.Role;
import com.lazy.rest.user.User;
import com.lazy.rest.user.UserRepository;
import com.lazy.rest.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Demo data for the {@code dev} profile only: users for every role, a handful of articles and a
 * well-known API key for the editor (so MCP clients work out of the box).
 *
 * <pre>
 *   admin  / admin123   ROLE_ADMIN
 *   editor / editor123  ROLE_EDITOR   (+ API key from lazyrest.dev.api-key)
 *   alice  / alice123   ROLE_USER
 *   bob    / bob123456  ROLE_USER
 * </pre>
 */
@Component
@Profile("dev")
@ConditionalOnBooleanProperty("lazyrest.dev.seed-data")
@Order(-10)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final UserService userService;
    private final ArticleRepository articles;
    private final ApiKeyService apiKeyService;
    private final String devApiKey;

    public DevDataSeeder(UserRepository users, UserService userService, ArticleRepository articles,
            ApiKeyService apiKeyService, @Value("${lazyrest.dev.api-key:}") String devApiKey) {
        this.users = users;
        this.userService = userService;
        this.articles = articles;
        this.apiKeyService = apiKeyService;
        this.devApiKey = devApiKey;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        User admin = userService.createUser("admin", "admin@lazyrest.local", "admin123", "Admin", Set.of(Role.ADMIN));
        User editor = userService.createUser("editor", "editor@lazyrest.local", "editor123", "Eddie Editor", Set.of(Role.EDITOR));
        User alice = userService.createUser("alice", "alice@lazyrest.local", "alice123", "Alice", Set.of(Role.USER));
        userService.createUser("bob", "bob@lazyrest.local", "bob123456", "Bob", Set.of(Role.USER));

        published("Getting started with LazyREST",
                "Run ./mvnw spring-boot:run and open Swagger UI.",
                "LazyREST starts with an in-memory H2 database, demo users and this article. "
                        + "Log in as admin/admin123, click Authorize in Swagger UI and try the Articles endpoints.", admin);
        published("Exposing Spring services as MCP tools",
                "How LazyREST lets AI agents call your business API safely.",
                "The Model Context Protocol (MCP) lets agents such as Claude Code discover and call tools. "
                        + "LazyREST exposes ArticleService methods as MCP tools over Streamable HTTP at /mcp, "
                        + "secured by API keys, gated by permissions and fully audited.", editor);
        published("RFC 9457 problem details in practice",
                "Consistent, machine-readable error responses.",
                "Every error is returned as application/problem+json with a stable code, the request id and, "
                        + "for validation errors, a list of field errors.", editor);
        published("用 Flyway 管理数据库结构",
                "告别 ddl-auto，让每一次表结构变更都可追溯。",
                "LazyREST 使用 Flyway 迁移脚本管理 MySQL / PostgreSQL / H2 的表结构，Hibernate 只做校验。", admin);
        published("Refresh token rotation explained",
                "Short-lived JWTs plus rotating opaque refresh tokens.",
                "Access tokens live 15 minutes. Refresh tokens are random, stored hashed, rotated on every use, "
                        + "and reuse of a revoked token revokes the whole session family.", editor);
        articles.save(new Article("Draft: roadmap ideas", "Only editor can see this draft.",
                "OAuth2 authorization for MCP, multi-tenancy, Redis-backed rate limiting.", editor));
        articles.save(new Article("Alice's private draft", "Visible to alice and admins only.",
                "Work in progress.", alice));

        if (devApiKey != null && !devApiKey.isBlank()) {
            apiKeyService.store(editor, "dev editor key", devApiKey, false);
        }
        log.info("Dev data seeded: users admin/admin123, editor/editor123, alice/alice123, bob/bob123456; "
                + "editor API key configured via lazyrest.dev.api-key");
    }

    private void published(String title, String summary, String content, User author) {
        Article article = new Article(title, summary, content, author);
        article.publish();
        articles.save(article);
    }
}

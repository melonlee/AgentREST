# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [2.0.0] - 2026-11-01

A complete rewrite. LazyREST is now an honest, production-shaped Spring Boot REST starter whose
endpoints are also MCP tools for AI agents.

### Breaking changes

- **Java 21** is required (was Java 8).
- **Spring Boot 4.1.2** (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) replaces Spring Boot 2.7.
- **Flyway** owns the database schema; `ddl-auto: update` is gone (Hibernate now only validates).
  Existing v1 databases are not migrated automatically.
- Configuration is profile- and environment-based: `dev` (default, H2) and `prod` (`DB_URL`,
  `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` required). Hardcoded credentials and secrets were removed.
- The servlet context path `/api` was removed; endpoints are mapped as `/api/...` and MCP lives at `/mcp`.
- Successful responses are plain JSON (no `ApiResponse` wrapper); errors are RFC 9457 problem details.
- Package layout reorganised into `auth`, `user`, `article`, `audit`, `mcp`, `common`.

### Added

- MCP server (Spring AI 2.0.2, Streamable HTTP) with `searchArticles`, `getArticle` and
  `createArticleDraft` tools, authenticated by API key or JWT, write tools gated by flag + permission,
  every call audited.
- Personal API keys (hashed, revocable, optional read-only).
- JWT access tokens via Spring Security's resource server + rotating opaque refresh tokens with reuse detection, logout, optional registration, `/api/auth/me`.
- Per-IP rate limiting for login / refresh / register.
- RBAC with roles and fine-grained permissions, method security, admin user management, bootstrap admin from env vars.
- Example `article` module: CRUD, validation, paging + sort whitelist, search, ownership checks, optimistic locking, publishing.
- Persisted, queryable audit log (`@Audited`), including denied and failed operations and the request channel.
- RFC 9457 ProblemDetail errors with `code`, `requestId` and field errors; `X-Request-Id` tracing.
- Flyway migrations for MySQL (also used by dev H2) and PostgreSQL.
- springdoc OpenAPI 3 / Swagger UI with JWT authorization; Actuator + Prometheus (admin only except health).
- Maven Wrapper, multi-stage Dockerfile, docker-compose (app + MySQL 8.4), GitHub Actions CI with Testcontainers.
- 68 automated tests (plus MySQL/PostgreSQL Testcontainers tests in CI).
- Bilingual README, `docs/decisions.md`.

### Removed

- `EncryptUtils` (AES/ECB and MD5 - insecure), `StringUtils`, `DateUtils`, `ValidatorUtils` and other unused utilities.
- Druid, Redis, Guava, commons-io, MapStruct, Lombok and jjwt dependencies.
- README claims for features that never existed (XSS/SQL-injection filters, Redis caching, file upload, RSA, API versioning).
- The Java 8 `maven.yml` workflow, Jekyll `_config.yml` and the outdated v1 architecture diagram.

## [1.0.0]

- Initial Spring Boot 2.7 / Java 8 scaffold.

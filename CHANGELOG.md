# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [2.1.0] - 2026-11-01

LazyREST is now **AgentREST** - "Spring Boot REST + MCP starter" / 「你的业务 API，让 AI Agent 安全调用」.
Same code, same REST API, same database schema; the beaver mascot stays (now joining two plugs - MCP as the "USB-C for AI"). The GitHub repository moves to
`melonlee/AgentREST` (old URLs redirect) and the website to <https://melonlee.github.io/AgentREST/>.

### Changed (breaking for template users)

- Java package `com.lazy.rest` → `com.fantasyailab.agentrest`; main class `LazyRestApplication` → `AgentRestApplication`.
- Maven coordinates `com.lazy.rest:lazy-rest` → `com.fantasyailab:agentrest`; the jar is now `target/agentrest-2.1.0.jar`.
- `spring.application.name`, MCP server name (`serverInfo.name`) and JWT issuer `lazyrest` → `agentrest`; MCP instructions,
  OpenAPI title (`AgentREST API`) and log output use the new name. Access tokens issued by 2.0 fail issuer validation -
  clients log in again or refresh (refresh tokens are opaque and stay valid).
- Configuration prefix `lazyrest.*` → `agentrest.*` (e.g. `agentrest.mcp.write-tools-enabled`, `agentrest.security.jwt.*`).
- Env vars `LAZYREST_ADMIN_USERNAME` / `_EMAIL` / `_PASSWORD` → `AGENTREST_ADMIN_*`; the old names are still read as a fallback.
- Docker image / compose image `agentrest:2.1.0`, container user `agentrest`, compose database and user `agentrest`
  (existing compose volumes created as `lazyrest` keep their old database name - set `DB_URL` / `DB_USERNAME` accordingly).
- Newly issued API keys use the **`ar_`** prefix. Keys with the legacy **`lr_`** prefix are still accepted
  (`X-API-Key` and `Authorization: Bearer`), so existing keys keep working with no data migration.
- Dev demo API key is now `ar_dev_editor_0123456789abcdefghijklmnop`; the dev profile also seeds the old
  `lr_dev_editor_…` key (`agentrest.dev.legacy-api-key`).

### Migration notes

- **Released Flyway migrations are unchanged** (checksums intact), so v2.0.0 databases upgrade in place.
- MCP clients: re-register the server under the new name, e.g.
  `claude mcp add --transport http agentrest http://localhost:8080/mcp --header "X-API-Key: ar_…"`, rename the
  `.mcp.json` / Claude Desktop key to `agentrest` and tool allow-lists to `mcp__agentrest__…`.
- Projects generated from the template before the rename: nothing forces you to follow; to pick up the
  legacy-prefix support, port `ApiKeyService.hasApiKeyPrefix` and its two call sites.

### Added

- Tests for `ar_`-prefixed key issuance and legacy `lr_` acceptance over MCP.
- New brand assets under the new name: `agentrest` wordmark, plug-joining beaver icon / mark / favicon set and
  lockups, README banner, social preview, share card, Chinese poster and architecture diagram
  (`docs/assets/brand/agentrest-*`; the `lazyrest-*` files are gone).

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

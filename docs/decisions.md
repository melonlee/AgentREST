# Technical decisions (v2.0.0)

Versions verified against start.spring.io / Maven Central / spring.io docs in November 2026.

| Area | Choice | Why |
|---|---|---|
| Language | **Java 21** (LTS) | Required baseline; records, pattern matching, virtual threads (`spring.threads.virtual.enabled=true`). Boot 4 also runs on 25. |
| Framework | **Spring Boot 4.1.2** (Spring Framework 7, Spring Security 7.1, Hibernate 7.4, Jackson 3) | Newest GA line that the newest GA Spring AI supports. Boot 4.2 is RC only, and Spring AI 2.0.x declares `Spring Boot >=4.0.0 and <4.2.0-M1`. Boot 2.7 (v1) is out of OSS support. |
| AI / MCP | **Spring AI 2.0.2** `spring-ai-starter-mcp-server-webmvc` (MCP Java SDK 2.1.0), `@McpTool` annotations | Latest GA (2.1 is RC). Annotation scanning gives JSON schemas + tool hints with no boilerplate. |
| MCP transport | **Streamable HTTP** (`spring.ai.mcp.server.protocol=STREAMABLE`, endpoint `/mcp`) | Spring AI docs mark SSE as deprecated since 2.0 and recommend STREAMABLE; it is what Claude Code (`--transport http`), the MCP Inspector and `mcp-remote` speak. STATELESS is a possible switch for horizontally scaled deployments (see below). |
| MCP auth | Spring Security filter chain on `/mcp`: personal **API keys** (`X-API-Key` or `Bearer lr_…`) or the normal JWT | Spring AI's transport is auth-agnostic by design. The community `mcp-security` module is pre-1.0; plain Spring Security keeps the model transparent. OAuth 2.1 for MCP is on the roadmap. |
| Tool execution | Tools are thin adapters over the **same service layer** as REST, run through `McpToolExecutor` | One place for permissions (`@PreAuthorize`), ownership, validation and auditing. The caller's `Authentication` is copied into the MCP transport context so tools never depend on which thread the SDK uses. |
| JWT | Spring Security OAuth2 Resource Server + Nimbus, **HS256**, 15 min access tokens | No hand-rolled JWT parsing. Refresh tokens are opaque 256-bit values, stored as SHA-256, rotated on every use with reuse detection. |
| Persistence | Spring Data JPA + **Flyway 12**, `ddl-auto=validate` | Schema changes are reviewed SQL; Hibernate only validates. Vendor folders `db/migration/mysql` (also used by dev H2 in `MODE=MySQL`) and `db/migration/postgresql`. |
| Errors | RFC 9457 `ProblemDetail` + `code`, `requestId`, `timestamp`, `errors[]` | Standard, machine-readable, correlates with logs and audit entries. |
| API docs | **springdoc-openapi 3.2.0** | The springdoc line for Boot 4; Swagger UI with a JWT "Authorize" button. Disabled by default in `prod`. |
| Rate limiting | Small in-memory fixed-window limiter on login/refresh/register | No extra infrastructure. Per instance - documented; swap for Bucket4j + Redis when running replicas. |
| Caching / Redis | **Not included** | Nothing in the example needs it, so v2 does not claim it. |
| Tests | JUnit 5, `@WebMvcTest`, `@DataJpaTest`, full-stack `@SpringBootTest` over HTTP, MCP Java SDK client, **Testcontainers 2** (MySQL 8.4, PostgreSQL 17) | Local `./mvnw verify` needs no Docker; the container tests are tagged `docker` and run in CI via `-Ptestcontainers`. |
| Packaging | Multi-stage Dockerfile, Spring Boot layered extraction (`-Djarmode=tools`), non-root user | Small rebuilds, no JDK in the runtime image. |

## Notes and trade-offs

- **Authorities live in the JWT.** Role changes take effect at the next refresh (<= 15 min). Disabling a user blocks login and refresh immediately.
- **Spring Security 7 publishes RFC 9728 metadata** at `/.well-known/oauth-protected-resource` automatically. LazyREST does not advertise it in `WWW-Authenticate` because there is no authorization server yet - otherwise OAuth-capable MCP clients would try a login flow that cannot succeed.
- **MCP sessions are in memory** (Streamable HTTP). With several replicas use sticky sessions, or switch to `protocol: STATELESS` (then replace `McpServerConfig`'s transport bean with the stateless variant and keep the same context extractor).
- **Graceful shutdown:** open MCP SSE streams are closed by a `SmartLifecycle` before Tomcat's graceful shutdown, otherwise they would hold it for the full timeout.
- **Write tools have three gates:** `lazyrest.mcp.write-tools-enabled` (off in prod by default), the `mcp:write` permission (and a non read-only key), and the service-level `article:write` check. Agents can only create drafts; publishing is REST-only.

<!-- banner -->

# LazyREST

> An **honest, production-shaped** Spring Boot REST starter where your endpoints are also **MCP tools for AI agents**.

[![CI](https://github.com/melonlee/LazyREST/actions/workflows/ci.yml/badge.svg)](https://github.com/melonlee/LazyREST/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![Spring AI 2.0 MCP](https://img.shields.io/badge/Spring%20AI-2.0%20MCP-blue)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[简体中文](README.md) | English

LazyREST v2 stands on two pillars:

1. **A small but genuinely production-grade REST baseline** - runs with one command, everything is tested, and every feature listed here actually exists.
2. **AI-ready** - selected service methods are exposed as **MCP tools** (Model Context Protocol) through Spring AI's MCP server, so Claude Code, Claude Desktop, Cursor and other agents can call your business API. Permissions, ownership checks, validation and auditing are exactly the same as for REST.

<!-- banner: screenshot of Claude Code calling a LazyREST MCP tool -->

---

## Features

Every item below is implemented and covered by automated tests.

| Module | What you get |
|---|---|
| **auth** | Username/password login (BCrypt), JWT access tokens (HS256, 15 min), **opaque refresh tokens** (hashed, rotated on every use, reuse detection revokes the whole family), logout, optional self-registration, `/api/auth/me`; per-IP **rate limiting** on login/refresh/register (429 + `Retry-After`) |
| **API keys** | Personal API keys (`lr_` prefix, SHA-256 at rest, shown once, revocable, optional **read-only**) for MCP clients |
| **user** | RBAC: roles (`ROLE_ADMIN` / `ROLE_EDITOR` / `ROLE_USER`) + fine-grained permissions (`article:write`, `audit:read`, `mcp:write`, ...), `@PreAuthorize` method security, admin user management, first admin bootstrapped from env vars |
| **article** (example domain) | CRUD, Jakarta Validation, paging + whitelisted sorting, search (escaped LIKE), status/author filters, **ownership checks** (others' drafts are 404, editing others' articles is 403), optimistic locking, publish workflow |
| **audit** | `@Audited` AOP: who / what / resource / when / outcome (SUCCESS / DENIED / FAILURE) / channel (REST / MCP) / request id / IP / duration, persisted in its own transaction, queryable by admins |
| **common** | RFC 9457 `application/problem+json` errors (with `code`, `requestId`, field `errors`), `X-Request-Id` tracing (logging MDC), one paging DTO |
| **mcp** | Streamable HTTP MCP endpoint `/mcp`: `searchArticles`, `getArticle`, `createArticleDraft`; authentication required (API key or JWT); write tools behind three gates; **every tool call is audited** |
| **persistence** | Spring Data JPA + **Flyway** (no `ddl-auto`, Hibernate only validates); dev = in-memory H2 + demo data; prod = MySQL 8 (PostgreSQL works too) |
| **ops** | springdoc OpenAPI 3 + Swagger UI (JWT "Authorize" button), Actuator (only health is public), Prometheus metrics, graceful shutdown, virtual threads |
| **delivery** | Maven Wrapper, multi-stage Dockerfile (layered jar, non-root), docker-compose (app + MySQL), GitHub Actions (incl. Testcontainers MySQL/PostgreSQL) |

**Deliberately not included** (so not claimed): caching / Redis, file upload, XSS filters, API versioning, multi-tenancy, an OAuth2 authorization server, MFA. See the [roadmap](#roadmap).

---

## 1-minute quick start

You only need **JDK 21** - no database, Redis or Docker:

```bash
git clone https://github.com/melonlee/LazyREST.git
cd LazyREST
./mvnw spring-boot:run
```

- Swagger UI: <http://localhost:8080/swagger-ui.html> (call `POST /api/auth/login`, then click **Authorize** and paste the `accessToken`)
- Health: <http://localhost:8080/actuator/health>
- MCP endpoint: `http://localhost:8080/mcp`

Demo users (created by the **dev profile only**, never in prod):

| Username / password | Role | Can |
|---|---|---|
| `admin` / `admin123` | ROLE_ADMIN | everything: user management, audit log, Actuator |
| `editor` / `editor123` | ROLE_EDITOR | write + publish articles, MCP write tools |
| `alice` / `alice123` | ROLE_USER | read articles, write own drafts |
| `bob` / `bob123456` | ROLE_USER | same as alice |

Demo API key (bound to `editor`, read + write): `lr_dev_editor_0123456789abcdefghijklmnop`

<!-- banner: Swagger UI screenshot -->

---

## A curl session

Verified locally (needs `jq`):

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123"}' | jq -r .accessToken)

curl -s -X POST localhost:8080/api/articles -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Hello LazyREST","summary":"My first draft","content":"Written with curl"}' | jq -c .
# {"id":8,"title":"Hello LazyREST",...,"status":"DRAFT","author":"alice",...,"version":0}

curl -s "localhost:8080/api/articles?q=hello&size=5&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN" | jq -c '{totalElements, titles: [.content[].title]}'
# {"totalElements":1,"titles":["Hello LazyREST"]}

curl -s -X POST localhost:8080/api/articles -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":""}' | jq .
# 400 application/problem+json: {"code":"VALIDATION_FAILED","requestId":"...","errors":[{"field":"content",...},{"field":"title",...}]}

curl -s -X POST localhost:8080/api/articles/8/publish -H "Authorization: Bearer $TOKEN" | jq -c '{status,code,detail}'
# {"status":403,"code":"FORBIDDEN","detail":"You do not have permission to perform this action"}

ADMIN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
curl -s "localhost:8080/api/audit-logs?actor=alice&size=5" -H "Authorization: Bearer $ADMIN" \
  | jq -c '.content[] | {actor, action, resourceId, channel, outcome}'
# {"actor":"alice","action":"ARTICLE_PUBLISH","resourceId":"8","channel":"REST","outcome":"DENIED"}
# {"actor":"alice","action":"ARTICLE_CREATE","resourceId":"8","channel":"REST","outcome":"SUCCESS"}
```

---

## Let AI agents call your API (MCP)

Agents do not get database access and do not bypass business rules: they call the **same service layer** as REST through MCP, with the same permissions, ownership checks, validation and audit trail.

<!-- banner: architecture diagram (REST client + AI agent -> Spring Security -> service layer -> DB, audit) -->

| Tool | Kind | Description |
|---|---|---|
| `searchArticles` | read | Search by text / status (published + own drafts), paged |
| `getArticle` | read | Full article by id; invisible drafts are "not found" |
| `createArticleDraft` | write | Creates a **draft** as the caller; publishing always needs a human (REST only) |

**Security model**

- `/mcp` requires `X-API-Key: lr_...` (or `Authorization: Bearer lr_...`) or a regular JWT; otherwise 401 ProblemDetail.
- The agent acts with the permissions of the key's owner; **read-only keys** keep only `*:read` permissions.
- Write tools pass three gates: `lazyrest.mcp.write-tools-enabled` (off by default in prod - the tool is then absent from `tools/list`), the `mcp:write` permission, and the service-level `article:write` check.
- Every tool call writes an `MCP_TOOL_CALL` audit entry (argument summary, outcome, duration); nested business operations are recorded with `channel=MCP`.
- Failures come back as `isError: true` with one readable sentence - no stack traces, no internals.

Create your own key (read-only recommended):

```bash
curl -s -X POST localhost:8080/api/api-keys -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"claude-code on my laptop","readOnly":true}' | jq .
```

### Claude Code

```bash
claude mcp add --transport http lazyrest http://localhost:8080/mcp \
  --header "X-API-Key: lr_dev_editor_0123456789abcdefghijklmnop"
```

Or commit a project `.mcp.json` and keep the key in the environment:

```json
{
  "mcpServers": {
    "lazyrest": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "X-API-Key": "${LAZYREST_API_KEY}"
      }
    }
  }
}
```

```bash
export LAZYREST_API_KEY=lr_dev_editor_0123456789abcdefghijklmnop
claude mcp list   # lazyrest: http://localhost:8080/mcp (HTTP) - ✓ Connected
```

### Claude Desktop

Claude Desktop's config file only launches local (stdio) servers, so bridge with [`mcp-remote`](https://github.com/geelen/mcp-remote) (requires Node.js). In `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "lazyrest": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp", "--header", "X-API-Key:${LAZYREST_API_KEY}"],
      "env": {
        "LAZYREST_API_KEY": "lr_dev_editor_0123456789abcdefghijklmnop"
      }
    }
  }
}
```

Other Streamable-HTTP clients (Cursor, ...): use the URL `http://localhost:8080/mcp` with an `X-API-Key` header; see your client's docs for the file format.

### MCP Inspector

```bash
npx -y @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http \
  --header "X-API-Key: lr_dev_editor_0123456789abcdefghijklmnop" --method tools/list

npx -y @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http \
  --header "X-API-Key: lr_dev_editor_0123456789abcdefghijklmnop" \
  --method tools/call --tool-name searchArticles --tool-arg query=mcp
```

### Prompts to try

- "Use lazyrest to find articles about MCP and summarize them."
- "Read article 2 and save a beginner-friendly rewrite as a draft with createArticleDraft."
- "List my drafts and tell me which ones are missing a summary."

Then see everything the agent did: `GET /api/audit-logs?channel=MCP`.

### Expose your own tool

```java
@McpTool(name = "getOrder", description = "Get one order by id, including its items and status.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
public CallToolResult getOrder(McpTransportContext ctx,
        @McpToolParam(description = "Order id") long id) {
    return executor.call("getOrder", ctx, Map.of("id", id), null, () -> orderService.get(id));
}
```

`McpToolExecutor` binds the caller's identity, checks tool-level permissions, audits the call and turns exceptions into model-friendly errors.

---

## Docker Compose (MySQL)

```bash
cp .env.example .env      # set JWT_SECRET (openssl rand -base64 48), DB passwords, admin password
docker compose up --build
```

The app runs with the `prod` profile against MySQL 8.4; Flyway creates the schema and the first admin is created from `LAZYREST_ADMIN_*`. Missing required variables stop both compose and the app with a message naming them.

PostgreSQL: set `DB_URL=jdbc:postgresql://host:5432/lazyrest` (Flyway picks `db/migration/postgresql`).

---

## Project structure

```
src/main/java/com/lazy/rest/
├── auth/            login / refresh / logout / register / me, JWT, rate limiting, SecurityConfig
│   └── apikey/      API key entity, service, filter, endpoints
├── user/            User / Role / Permission, user admin, bootstrap admin
├── article/         example domain module (delete it when you start your own)
├── audit/           @Audited, aspect, audit log entity and query endpoint
├── mcp/             MCP tools, McpToolExecutor, transport configuration
├── common/          api (paging, sort whitelist, LIKE escaping), error (ProblemDetail),
│                    security, web (RequestIdFilter), config (OpenAPI, prod guard)
└── dev/             DevDataSeeder (dev profile only)
src/main/resources/db/migration/{mysql,postgresql}/   Flyway scripts
docs/decisions.md    technical decisions and trade-offs
```

---

## Configuration

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (H2 + demo data) or `prod` |
| `DB_URL` | - | **required in prod**, e.g. `jdbc:mysql://db:3306/lazyrest` |
| `DB_USERNAME` / `DB_PASSWORD` | - | **required in prod** |
| `DB_POOL_SIZE` | `10` | Hikari pool size |
| `JWT_SECRET` | obvious dev-only value | **required in prod**, >= 32 bytes; the dev value is rejected with `prod` |
| `LAZYREST_ADMIN_USERNAME` / `_EMAIL` / `_PASSWORD` | - | creates the first admin on an empty database (password >= 12 chars) |
| `MCP_WRITE_TOOLS_ENABLED` | dev `true` / prod `false` | register MCP write tools |
| `REGISTRATION_ENABLED` | dev `true` / prod `false` | public self-registration |
| `API_DOCS_ENABLED` | prod `false` | Swagger UI / OpenAPI in prod |
| `CORS_ALLOWED_ORIGINS` | empty (off) | comma-separated origins |
| `FORWARD_HEADERS_STRATEGY` | `none` | set to `native` or `framework` behind a reverse proxy, otherwise rate limiting and audit see the proxy IP |

Tunables in `application.yml`: `lazyrest.security.jwt.access-token-ttl` (15m), `refresh-token-ttl` (14d), `lazyrest.auth.login-rate-limit.max-attempts` (10) / `window` (1m).

---

## Security notes

- **No secrets in the repository.** The dev JWT secret starts with `dev-only-` and is refused together with the `prod` profile.
- BCrypt via `DelegatingPasswordEncoder`. Unknown user and wrong password produce the same response.
- Access tokens live 15 minutes and carry the authorities: role changes apply at the next refresh; disabled users cannot log in or refresh.
- Refresh tokens and API keys are 256-bit random values stored as SHA-256; API keys are only accepted on `/mcp`.
- Rate limiting is **in-memory per instance**.
- CSRF is disabled because the API is stateless and uses bearer tokens only (no cookies). CORS is off by default.
- Errors never contain stack traces; a 500 returns only the `requestId` to correlate with logs.
- Only `health` is public among Actuator endpoints.
- **MCP caveats:** registering a tool means exposing it; data an agent reads enters the model's context; user-written content may contain prompt injection aimed at agents - which is why agents can only create drafts and a human publishes.
- Not covered: MFA, account lockout (beyond rate limiting), OAuth for MCP, TLS (terminate at your proxy).

---

## Testing

```bash
./mvnw verify                      # 68 tests on H2, no Docker needed
./mvnw -Ptestcontainers test       # MySQL 8.4 + PostgreSQL 17 via Testcontainers (Docker required)
```

Unit tests, `@WebMvcTest`, `@DataJpaTest` (real Flyway migrations + Hibernate validation), full-stack `@SpringBootTest` over HTTP (auth, refresh rotation/reuse, RBAC, ownership, paging, audit, rate limiting) and MCP tests that use the official MCP Java SDK client for `initialize` → `tools/list` → `tools/call`. CI additionally runs the whole flow with the `prod` profile on real MySQL and PostgreSQL.

---

## Use it as a template

1. Change `groupId` / `artifactId` / `name` in `pom.xml`.
2. Rename the `com.lazy.rest` package (IDE refactoring, or `git mv` + search/replace).
3. Remove the example module: the `article/` package, `mcp/ArticleMcpTools.java`, `mcp/ArticleWriteMcpTools.java`, the `articles` table (edit `V1__schema.sql` before your first deployment, or add a `DROP TABLE` migration afterwards), the article seed data in `DevDataSeeder` and the article-related tests.
4. Add your domain as `Entity + Repository + Service (@PreAuthorize / @Audited / @Validated) + Controller`, plus an `@McpTool` adapter when agents should use it.
5. Add new permissions in a Flyway migration.

---

## Roadmap

- [ ] OAuth 2.1 authorization for MCP (RFC 9728 metadata + authorization server) instead of static keys
- [ ] More MCP examples: resources and prompts
- [ ] Optional Redis: distributed rate limiting and caching
- [ ] Stateless MCP mode for horizontal scaling
- [ ] OpenTelemetry tracing
- [ ] Project generator (rename package / drop the example module in one step)

## Upgrading from v1

v2 is a rewrite and not compatible with v1: Java 21, Spring Boot 4.1, Flyway-managed schema, and the `/api` prefix moved from the servlet context path into controller mappings. See the [CHANGELOG](CHANGELOG.md). v1 remains in the git history.

## License

[Apache License 2.0](LICENSE)

---

Maintained by FantasyAI Lab. If LazyREST helps you, a ⭐ is appreciated - issues and PRs welcome.

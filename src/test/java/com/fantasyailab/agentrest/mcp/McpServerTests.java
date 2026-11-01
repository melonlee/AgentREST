package com.fantasyailab.agentrest.mcp;

import com.jayway.jsonpath.JsonPath;
import com.fantasyailab.agentrest.support.IntegrationTestSupport;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Talks to the running server at /mcp with the official MCP Java SDK client (Streamable HTTP),
 * exactly like Claude Code / Cursor would.
 */
class McpServerTests extends IntegrationTestSupport {

    private McpSyncClient client(String headerName, String headerValue) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        if (headerName != null) {
            requestBuilder.header(headerName, headerValue);
        }
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl())
                .endpoint("/mcp")
                .requestBuilder(requestBuilder)
                .build();
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("agentrest-tests", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperties(Tool tool) {
        return (Map<String, Object>) tool.inputSchema().get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> schemaRequired(Tool tool) {
        return (List<String>) tool.inputSchema().getOrDefault("required", List.of());
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    private static CallToolResult call(McpSyncClient client, String tool, Map<String, Object> args) {
        return client.callTool(new CallToolRequest(tool, args));
    }

    @Test
    void initializeAndListToolsWithApiKey() {
        try (McpSyncClient client = client("X-API-Key", EDITOR_API_KEY)) {
            McpSchema.InitializeResult init = client.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("agentrest");
            assertThat(init.instructions()).contains("searchArticles");
            assertThat(init.capabilities().tools()).isNotNull();

            List<Tool> tools = client.listTools().tools();
            assertThat(tools).extracting(Tool::name)
                    .containsExactlyInAnyOrder("searchArticles", "getArticle", "createArticleDraft");

            Tool search = tools.stream().filter(t -> t.name().equals("searchArticles")).findFirst().orElseThrow();
            assertThat(search.description()).contains("published articles");
            assertThat(search.annotations().readOnlyHint()).isTrue();
            assertThat(schemaProperties(search)).containsKeys("query", "status", "page", "size");
            assertThat(schemaRequired(search)).isEmpty();

            Tool create = tools.stream().filter(t -> t.name().equals("createArticleDraft")).findFirst().orElseThrow();
            assertThat(create.annotations().readOnlyHint()).isFalse();
            assertThat(schemaRequired(create)).containsExactlyInAnyOrder("title", "content");
        }
    }

    @Test
    void searchAndGetReturnTheSameDataAsTheRestApi() {
        try (McpSyncClient client = client("X-API-Key", EDITOR_API_KEY)) {
            client.initialize();

            CallToolResult search = call(client, "searchArticles", Map.of("query", "MCP tools"));
            assertThat(search.isError()).isFalse();
            String json = text(search);
            List<String> titles = JsonPath.read(json, "$.content[*].title");
            assertThat(titles).contains("Exposing Spring services as MCP tools");
            Number id = JsonPath.read(json, "$.content[0].id");

            CallToolResult article = call(client, "getArticle", Map.of("id", id));
            assertThat(article.isError()).isFalse();
            assertThat((String) JsonPath.read(text(article), "$.content")).contains("Model Context Protocol");
        }
    }

    @Test
    void createArticleDraftCreatesADraftOwnedByTheKeyOwnerAndIsAudited() {
        String title = "Agent draft " + UUID.randomUUID();
        try (McpSyncClient client = client("X-API-Key", EDITOR_API_KEY)) {
            client.initialize();
            CallToolResult result = call(client, "createArticleDraft",
                    Map.of("title", title, "summary", "written by an agent", "content", "Hello from MCP"));

            assertThat(result.isError()).isFalse();
            assertThat((String) JsonPath.read(text(result), "$.status")).isEqualTo("DRAFT");
            assertThat((String) JsonPath.read(text(result), "$.author")).isEqualTo("editor");
        }

        // visible to its author through REST, invisible to others
        String editor = editorToken();
        Response mine = get("/api/articles?q={q}", editor, title);
        assertThat(mine.<List<String>>json("$.content[*].title")).containsExactly(title);
        assertThat((Integer) get("/api/articles?q={q}", aliceToken(), title).json("$.totalElements")).isZero();

        // both the tool call and the nested service operation are audited with channel MCP
        String admin = adminToken();
        Response toolCalls = get("/api/audit-logs?action=MCP_TOOL_CALL&actor=editor&size=100", admin);
        assertThat(toolCalls.<List<String>>json("$.content[?(@.resourceId == 'createArticleDraft')].channel"))
                .isNotEmpty().allMatch("MCP"::equals);
        Response creates = get("/api/audit-logs?action=ARTICLE_CREATE&channel=MCP&actor=editor&size=100", admin);
        assertThat((Integer) creates.json("$.totalElements")).isPositive();
    }

    @Test
    void validationErrorsAreReturnedAsToolErrors() {
        try (McpSyncClient client = client("X-API-Key", EDITOR_API_KEY)) {
            client.initialize();
            CallToolResult result = call(client, "createArticleDraft", Map.of("title", " ", "content", "x"));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).isEqualTo("Invalid arguments: title must not be blank");
        }
    }

    @Test
    void otherUsersDraftsAreNotFoundViaMcp() {
        long aliceDraft = post("/api/articles", aliceToken(), Map.of("title", "Alice only", "content", "x")).id();

        try (McpSyncClient client = client("X-API-Key", EDITOR_API_KEY)) {
            client.initialize();
            CallToolResult result = call(client, "getArticle", Map.of("id", aliceDraft));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).isEqualTo("Article " + aliceDraft + " not found");
        }
    }

    @Test
    void newlyIssuedApiKeysUseTheArPrefixAndWorkAsBearer() {
        Response key = post("/api/api-keys", editorToken(), Map.of("name", "ar prefix agent"));
        assertThat(key.status()).isEqualTo(201);
        assertThat(key.str("$.apiKey")).startsWith("ar_");
        assertThat(key.str("$.prefix")).startsWith("ar_");

        try (McpSyncClient client = client("Authorization", "Bearer " + key.str("$.apiKey"))) {
            client.initialize();
            assertThat(client.listTools().tools()).isNotEmpty();
        }
    }

    @Test
    void legacyLrPrefixedApiKeysAreStillAccepted() {
        for (String[] header : new String[][] {
                {"X-API-Key", LEGACY_EDITOR_API_KEY}, {"Authorization", "Bearer " + LEGACY_EDITOR_API_KEY}}) {
            try (McpSyncClient client = client(header[0], header[1])) {
                assertThat(client.initialize().serverInfo().name()).isEqualTo("agentrest");
                assertThat(client.listTools().tools()).extracting(Tool::name).contains("searchArticles", "createArticleDraft");
                assertThat(call(client, "searchArticles", Map.of()).isError()).isFalse();
            }
        }
    }

    @Test
    void readOnlyApiKeyCannotUseWriteTools() {
        Response key = post("/api/api-keys", editorToken(), Map.of("name", "read-only agent", "readOnly", true));
        assertThat(key.status()).isEqualTo(201);
        assertThat((Boolean) key.json("$.readOnly")).isTrue();

        try (McpSyncClient client = client("X-API-Key", key.str("$.apiKey"))) {
            client.initialize();
            assertThat(call(client, "searchArticles", Map.of()).isError()).isFalse();

            CallToolResult write = call(client, "createArticleDraft", Map.of("title", "nope", "content", "nope"));
            assertThat(write.isError()).isTrue();
            assertThat(text(write)).startsWith("Permission denied: Missing permission mcp:write");
        }

        Response denied = get("/api/audit-logs?action=MCP_TOOL_CALL&outcome=DENIED&size=100", adminToken());
        assertThat(denied.<List<String>>json("$.content[*].resourceId")).contains("createArticleDraft");
    }

    @Test
    void usersWithoutMcpWritePermissionCannotUseWriteToolsEvenWithAFullKey() {
        Response key = post("/api/api-keys", aliceToken(), Map.of("name", "alice agent", "readOnly", false));

        try (McpSyncClient client = client("Authorization", "Bearer " + key.str("$.apiKey"))) {
            client.initialize();
            CallToolResult write = call(client, "createArticleDraft", Map.of("title", "nope", "content", "nope"));

            assertThat(write.isError()).isTrue();
            assertThat(text(write)).contains("mcp:write");
        }
    }

    @Test
    void jwtAccessTokensAreAcceptedToo() {
        try (McpSyncClient client = client("Authorization", "Bearer " + aliceToken())) {
            client.initialize();
            assertThat(call(client, "searchArticles", Map.of("query", "AgentREST")).isError()).isFalse();
        }
    }

    @Test
    void revokedApiKeysStopWorking() {
        String editor = editorToken();
        Response key = post("/api/api-keys", editor, Map.of("name", "short-lived"));
        assertThat(delete("/api/api-keys/" + key.id(), editor).status()).isEqualTo(204);

        try (McpSyncClient client = client("X-API-Key", key.str("$.apiKey"))) {
            assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void missingOrInvalidCredentialsAreRejectedBeforeReachingMcp() {
        try (McpSyncClient client = client(null, null)) {
            assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
        }

        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                 "capabilities":{},"clientInfo":{"name":"raw","version":"1"}}}""";
        Response anonymous = exchange(HttpMethod.POST, "/mcp", null, jsonMapper.readTree(initialize),
                h -> h.set("Accept", "application/json, text/event-stream"));
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.headers().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(anonymous.str("$.detail")).contains("X-API-Key");

        Response badKey = exchange(HttpMethod.POST, "/mcp", null, jsonMapper.readTree(initialize), h -> {
            h.set("Accept", "application/json, text/event-stream");
            h.set("X-API-Key", "ar_this_key_does_not_exist_000000");
        });
        assertThat(badKey.status()).isEqualTo(401);
        assertThat(badKey.str("$.detail")).isEqualTo("Invalid API key");
    }

    @Test
    void apiKeyEndpointNeverReturnsSecretsAfterCreation() {
        String editor = editorToken();
        post("/api/api-keys", editor, Map.of("name", "listing test"));

        Response list = get("/api/api-keys", editor);

        assertThat(list.status()).isEqualTo(200);
        assertThat(list.<List<String>>json("$[*].name")).contains("dev editor key", "listing test");
        assertThat(list.body()).doesNotContain("\"apiKey\"").doesNotContain(EDITOR_API_KEY);
    }
}

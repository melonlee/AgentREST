package com.lazy.rest.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpRequest;

import static com.lazy.rest.support.IntegrationTestSupport.EDITOR_API_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/** With the global flag off, write tools are not registered at all - agents cannot even see them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lazyrest.mcp.write-tools-enabled=false")
class McpWriteToolsDisabledTests {

    @LocalServerPort
    int port;

    @Test
    void onlyReadToolsAreListed() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("X-API-Key", EDITOR_API_KEY))
                .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();

            assertThat(client.listTools().tools()).extracting(Tool::name)
                    .containsExactlyInAnyOrder("searchArticles", "getArticle");
        }
    }
}

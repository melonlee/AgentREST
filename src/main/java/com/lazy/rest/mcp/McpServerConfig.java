package com.lazy.rest.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

/**
 * Replaces Spring AI's default Streamable-HTTP transport only to add a context extractor: the
 * {@link Authentication} established by the MCP security filter chain (API key or JWT) is copied
 * into the MCP transport context, so tool invocations run as the authenticated caller regardless
 * of which thread the MCP SDK executes them on.
 */
@Configuration(proxyBeanMethods = false)
public class McpServerConfig {

    public static final String AUTHENTICATION_KEY = "lazyrest.authentication";

    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    return authentication != null
                            ? McpTransportContext.create(Map.of(AUTHENTICATION_KEY, authentication))
                            : McpTransportContext.EMPTY;
                })
                .build();
    }

    /**
     * Long-lived MCP SSE streams would otherwise hold Tomcat's graceful shutdown until its timeout.
     * This lifecycle stops first (highest phase) and closes all MCP sessions, so in-flight REST
     * requests can still drain normally.
     */
    @Bean
    SmartLifecycle mcpSessionsShutdown(WebMvcStreamableServerTransportProvider transportProvider) {
        return new SmartLifecycle() {
            private volatile boolean running;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                try {
                    transportProvider.closeGracefully().block(Duration.ofSeconds(5));
                } catch (RuntimeException e) {
                    LoggerFactory.getLogger(McpServerConfig.class).debug("Closing MCP sessions failed", e);
                } finally {
                    running = false;
                }
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE;
            }
        };
    }
}

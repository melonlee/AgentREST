package com.lazy.rest.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

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
}

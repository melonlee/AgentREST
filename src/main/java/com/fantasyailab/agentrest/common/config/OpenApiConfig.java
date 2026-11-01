package com.fantasyailab.agentrest.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 3 metadata. Swagger UI gets an "Authorize" button for the JWT access token. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI agentRestOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        String version = buildProperties.getIfAvailable() != null ? buildProperties.getObject().getVersion() : "dev";
        return new OpenAPI()
                .info(new Info()
                        .title("AgentREST API")
                        .version(version)
                        .description("""
                                Spring Boot REST + MCP starter. \
                                1) POST /api/auth/login (e.g. admin / admin123 in dev) \
                                2) click **Authorize** and paste the `accessToken`. \
                                The same article operations are also exposed to AI agents as MCP tools at `/mcp`.""")
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}

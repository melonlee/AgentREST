package com.fantasyailab.agentrest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AgentREST - a production-shaped Spring Boot REST starter whose endpoints are also MCP tools.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentRestApplication.class, args);
    }
}

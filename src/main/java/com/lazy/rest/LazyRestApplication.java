package com.lazy.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LazyREST - a production-shaped Spring Boot REST starter whose endpoints are also MCP tools.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LazyRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(LazyRestApplication.class, args);
    }
}

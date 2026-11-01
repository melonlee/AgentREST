package com.lazy.rest.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresProdTests extends AbstractProdDatabaseTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("lazyrest");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerProdEnvironment(registry, POSTGRES);
    }
}

package com.fantasyailab.agentrest.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class MySqlProdTests extends AbstractProdDatabaseTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("agentrest");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerProdEnvironment(registry, MYSQL);
    }
}

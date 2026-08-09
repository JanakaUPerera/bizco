package com.bizco.server.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class PostgresIntegrationTest {

    public static final String POSTGRES_IMAGE = "postgres:16-alpine";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("bizco")
            .withUsername("bizco")
            .withPassword("bizco");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("bizco.initial-admin-password", () -> "");
        registry.add("bizco.security.max-concurrent-sessions", () -> "3");
    }

    protected String postgresImage() {
        return POSTGRES.getDockerImageName();
    }
}

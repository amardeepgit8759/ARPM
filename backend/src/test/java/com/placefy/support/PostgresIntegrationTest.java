package com.placefy.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p>The container is a singleton started once per JVM rather than a {@code @Container} field,
 * which would start and stop one per test class. Ryuk reaps it when the JVM exits.
 *
 * <p>Nothing here creates a schema: Flyway runs the same migrations the application runs, and
 * Hibernate is set to {@code validate}, so a mapping that has drifted from the migration fails
 * these tests rather than surfacing in production.
 */
@Tag("integration")
@SpringBootTest
public abstract class PostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("placefy")
            .withUsername("placefy")
            .withPassword("placefy");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

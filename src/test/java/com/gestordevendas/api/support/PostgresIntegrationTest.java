package com.gestordevendas.api.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Tag("integration")
public abstract class PostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("gestor_test")
        .withUsername("gestor")
        .withPassword("gestor");

    static {
        POSTGRES.start();
    }

    @Autowired
    JdbcTemplate cleanupJdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        cleanupJdbcTemplate.execute("""
        TRUNCATE TABLE
            api_internal.auditoria,
            api_internal.convites_usuario,
            api_internal.convites_empresa,
            api_internal.password_reset_tokens,
            api_internal.refresh_tokens,
            public.empresa_usuarios,
            api_internal.usuarios,
            public.empresas
        CASCADE
        """);
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
            "app.security.jwt-secret-base64",
            () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );
        registry.add("app.mail.from-email", () -> "test@example.com");
    }
}

package com.gestordevendas.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesIdentityAndTenancySchema() {
        assertTableExists("public", "empresas");
        assertTableExists("api_internal", "usuarios");
        assertTableExists("public", "empresa_usuarios");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1'",
                Integer.class
        )).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '2'",
                Integer.class
        )).isEqualTo(1);

        assertTableExists("api_internal", "refresh_tokens");
        assertTableExists("api_internal", "password_reset_tokens");
        assertTableExists("api_internal", "convites_empresa");
        assertTableExists("api_internal", "convites_usuario");
        assertTableExists("api_internal", "auditoria");

        assertTableExists("public", "clientes");
        assertTableExists("public", "produto_grupos");
        assertTableExists("public", "produtos");
        assertTableExists("public", "produto_fotos");
        assertTableExists("public", "cliente_produto_preco");
        assertTableExists("public", "cliente_produtos_ocultos");

        assertTableExists("public", "compras");
        assertTableExists("public", "compra_itens");
        assertTableExists("api_internal", "compra_estados");
        assertTableExists("api_internal", "movimentacoes_estoque");
        assertTableExists("public", "vendas");
        assertTableExists("public", "venda_itens");
        assertTableExists("public", "venda_recebimentos");
        assertTableExists("public", "recebiveis_manuais");
        assertTableExists("public", "recebivel_manual_pagamentos");
        assertTableExists("api_internal", "venda_sequencias");

        assertTableExists("public", "pedidos_cliente");
        assertTableExists("public", "pedido_cliente_itens");
        assertTableExists("public", "plataforma_administradores");
        assertTableExists("public", "profiles");
        assertTableExists("api_internal", "venda_estados");
        assertTableExists("api_internal", "pedido_locks");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '3'",
                Integer.class
        )).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '4'",
                Integer.class
        )).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'clientes'
                  and column_name = 'pedido_token'
                  and is_nullable = 'NO'
                """,
                Integer.class
        )).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_schema = 'public'
                  and constraint_name = 'fk_produtos_categoria_empresa'
                """,
                Integer.class
        )).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_schema = 'public'
                  and constraint_name = 'fk_cliente_preco_cliente_empresa'
                """,
                Integer.class
        )).isEqualTo(1);

        assertTokenHashColumnIsVarchar64("refresh_tokens");
        assertTokenHashColumnIsVarchar64("password_reset_tokens");
        assertTokenHashColumnIsVarchar64("convites_empresa");
        assertTokenHashColumnIsVarchar64("convites_usuario");
    }

    private void assertTableExists(String schema, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = ?
                  and table_name = ?
                  and table_type = 'BASE TABLE'
                """,
                Integer.class,
                schema,
                tableName
        );

        assertThat(count).isEqualTo(1);
    }

    private void assertTokenHashColumnIsVarchar64(String tableName) {
        String dataType = jdbcTemplate.queryForObject(
                """
                select data_type
                from information_schema.columns
                where table_schema = 'api_internal'
                  and table_name = ?
                  and column_name = 'token_hash'
                """,
                String.class,
                tableName
        );

        Integer maxLength = jdbcTemplate.queryForObject(
                """
                select character_maximum_length
                from information_schema.columns
                where table_schema = 'api_internal'
                  and table_name = ?
                  and column_name = 'token_hash'
                """,
                Integer.class,
                tableName
        );

        assertThat(dataType).isEqualTo("character varying");
        assertThat(maxLength).isEqualTo(64);
    }
}
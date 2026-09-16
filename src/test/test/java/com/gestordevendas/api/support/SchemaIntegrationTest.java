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
        assertThat(jdbcTemplate.queryForObject("select to_regclass('public.empresas')::text", String.class))
            .isEqualTo("empresas");
        assertThat(jdbcTemplate.queryForObject("select to_regclass('public.usuarios')::text", String.class))
            .isEqualTo("usuarios");
        assertThat(jdbcTemplate.queryForObject("select to_regclass('public.empresa_usuarios')::text", String.class))
            .isEqualTo("empresa_usuarios");
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where version = '1'", Integer.class))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where version = '2'", Integer.class))
            .isEqualTo(1);

        assertTableExists("clientes");
        assertTableExists("categorias_produtos");
        assertTableExists("produtos");
        assertTableExists("produto_fotos");
        assertTableExists("cliente_produto_preco");
        assertTableExists("compras");
        assertTableExists("compra_itens");
        assertTableExists("movimentacoes_estoque");
        assertTableExists("vendas");
        assertTableExists("venda_itens");
        assertTableExists("venda_pagamentos");
        assertTableExists("recebiveis_manuais");
        assertTableExists("recebivel_manual_pagamentos");
        assertTableExists("venda_sequencias");
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where version = '3'", Integer.class))
            .isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from information_schema.table_constraints where table_schema='public' and constraint_name='fk_produtos_categoria_empresa'", Integer.class))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from information_schema.table_constraints where table_schema='public' and constraint_name='fk_cliente_preco_cliente_empresa'", Integer.class))
            .isEqualTo(1);

        assertTokenHashColumnIsVarchar64("refresh_tokens");
        assertTokenHashColumnIsVarchar64("password_reset_tokens");
        assertTokenHashColumnIsVarchar64("convites_empresa");
        assertTokenHashColumnIsVarchar64("convites_usuario");
    }

    private void assertTableExists(String tableName) {
        assertThat(jdbcTemplate.queryForObject("select to_regclass(?)::text", String.class, "public." + tableName))
            .isEqualTo(tableName);
    }

    private void assertTokenHashColumnIsVarchar64(String tableName) {
        String dataType = jdbcTemplate.queryForObject(
            """
            select data_type
            from information_schema.columns
            where table_schema = 'public'
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
            where table_schema = 'public'
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

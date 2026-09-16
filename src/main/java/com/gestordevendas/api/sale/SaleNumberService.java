package com.gestordevendas.api.sale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SaleNumberService {
    private final JdbcTemplate jdbcTemplate;
    public SaleNumberService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public long next(UUID companyId) {
        Long value = jdbcTemplate.queryForObject(
            "insert into venda_sequencias (empresa_id, ultimo_numero) values (?, 1) " +
                "on conflict (empresa_id) do update set ultimo_numero = venda_sequencias.ultimo_numero + 1 returning ultimo_numero",
            Long.class, companyId);
        return value == null ? 1L : value;
    }
}

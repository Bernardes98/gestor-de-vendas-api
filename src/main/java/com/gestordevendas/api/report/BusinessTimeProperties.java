package com.gestordevendas.api.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.business")
public record BusinessTimeProperties(String zoneId) {
    public String effectiveZoneId() {
        return zoneId == null || zoneId.isBlank() ? "America/Sao_Paulo" : zoneId;
    }
}

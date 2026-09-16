package com.gestordevendas.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.r2")
public record R2Properties(
    boolean enabled,
    String endpoint,
    String accessKeyId,
    String secretAccessKey,
    String bucket,
    String publicBaseUrl
) {}

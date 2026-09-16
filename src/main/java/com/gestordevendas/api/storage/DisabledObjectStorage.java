package com.gestordevendas.api.storage;

import com.gestordevendas.api.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class DisabledObjectStorage implements ObjectStorage {
    private final R2Properties properties;

    public DisabledObjectStorage(R2Properties properties) {
        this.properties = properties;
    }

    @Override
    public String put(String key, String contentType, byte[] content) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "Armazenamento de imagens não configurado.");
    }

    @Override
    public void delete(String key) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "Armazenamento de imagens não configurado.");
    }

    @Override
    public String publicUrl(String key) {
        String base = properties.publicBaseUrl();
        return base == null || base.isBlank() ? key : base.replaceAll("/$", "") + "/" + key;
    }
}

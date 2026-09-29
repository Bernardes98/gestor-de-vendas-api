package com.gestordevendas.api.storage;

import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DatabaseObjectStorage implements ObjectStorage {
    private final DatabaseStoredObjectRepository repository;
    private final String publicBaseUrl;

    public DatabaseObjectStorage(DatabaseStoredObjectRepository repository, String publicBaseUrl) {
        this.repository = repository;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    @Override
    @Transactional
    public String put(String key, String contentType, byte[] content) {
        repository.findByObjectKey(key).ifPresent(repository::delete);
        repository.save(DatabaseStoredObject.create(key, contentType, content));
        return publicUrl(key);
    }

    @Override
    @Transactional
    public void delete(String key) {
        repository.deleteByObjectKey(key);
    }

    @Override
    public String publicUrl(String key) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return publicBaseUrl + "/api/public/media/" + encoded;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:8080";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

package com.gestordevendas.api.storage;

public interface ObjectStorage {
    String put(String key, String contentType, byte[] content);
    void delete(String key);
    String publicUrl(String key);
}

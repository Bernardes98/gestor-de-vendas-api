package com.gestordevendas.api.storage;

public record StoredObject(String key, String url, String contentType, long sizeBytes) {}

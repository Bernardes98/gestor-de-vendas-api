package com.gestordevendas.api.product;

import java.util.UUID;

public record ProductPhotoResponse(UUID id, String url, String contentType, long sizeBytes, int order) {}

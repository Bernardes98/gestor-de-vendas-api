package com.gestordevendas.api.category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, int order) {
    static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getOrderIndex());
    }
}

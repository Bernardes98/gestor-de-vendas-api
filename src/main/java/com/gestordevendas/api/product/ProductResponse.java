package com.gestordevendas.api.product;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductResponse(
    UUID id,
    String name,
    String code,
    String brand,
    String description,
    UUID categoryId,
    String categoryName,
    BigDecimal salePrice,
    boolean stockControlled,
    BigDecimal currentStock,
    BigDecimal minimumStock,
    BigDecimal costPrice,
    BigDecimal marginPercent,
    List<ProductPhotoResponse> photos
) {}

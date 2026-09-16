package com.gestordevendas.api.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicOrderCatalogResponse(
    String companyName, String companyLogoUrl, String primaryColor, String secondaryColor,
    String clientName, List<Product> products
) {
    public record Product(UUID id, String name, String brand, String sku, BigDecimal price, List<String> photoUrls) {}
}

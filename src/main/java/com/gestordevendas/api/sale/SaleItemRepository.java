package com.gestordevendas.api.sale;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {
    @EntityGraph(attributePaths = {"product"})
    List<SaleItem> findAllByCompanyIdAndSaleId(UUID companyId, UUID saleId);
    void deleteAllByCompanyIdAndSaleId(UUID companyId, UUID saleId);
}

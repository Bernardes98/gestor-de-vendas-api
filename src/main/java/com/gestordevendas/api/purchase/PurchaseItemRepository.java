package com.gestordevendas.api.purchase;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, UUID> {
    @EntityGraph(attributePaths = {"product"})
    List<PurchaseItem> findAllByCompanyIdAndPurchaseId(UUID companyId, UUID purchaseId);
    void deleteAllByCompanyIdAndPurchaseId(UUID companyId, UUID purchaseId);
}

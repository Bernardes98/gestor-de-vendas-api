package com.gestordevendas.api.purchase;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    @EntityGraph(attributePaths = {"company"})
    Optional<Purchase> findByIdAndCompanyId(UUID id, UUID companyId);
    @EntityGraph(attributePaths = {"company"})
    List<Purchase> findAllByCompanyIdOrderByPurchasedAtDesc(UUID companyId);
}

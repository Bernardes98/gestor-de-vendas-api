package com.gestordevendas.api.stock;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    @EntityGraph(attributePaths = {"product"})
    List<StockMovement> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}

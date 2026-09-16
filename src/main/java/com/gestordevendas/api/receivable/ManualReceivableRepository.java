package com.gestordevendas.api.receivable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManualReceivableRepository extends JpaRepository<ManualReceivable, UUID> {
    @EntityGraph(attributePaths = {"client"})
    Optional<ManualReceivable> findByIdAndCompanyId(UUID id, UUID companyId);
    @EntityGraph(attributePaths = {"client"})
    List<ManualReceivable> findAllByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, ManualReceivableStatus status);
}

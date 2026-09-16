package com.gestordevendas.api.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientHiddenProductRepository extends JpaRepository<ClientHiddenProduct, UUID> {
    @EntityGraph(attributePaths = "product")
    List<ClientHiddenProduct> findAllByCompanyIdAndClientId(UUID companyId, UUID clientId);
    boolean existsByCompanyIdAndClientIdAndProductId(UUID companyId, UUID clientId, UUID productId);
    void deleteAllByCompanyIdAndClientId(UUID companyId, UUID clientId);
}

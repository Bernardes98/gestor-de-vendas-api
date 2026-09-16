package com.gestordevendas.api.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientProductPriceRepository extends JpaRepository<ClientProductPrice, UUID> {
    Optional<ClientProductPrice> findByCompanyIdAndClientIdAndProductId(UUID companyId, UUID clientId, UUID productId);
    List<ClientProductPrice> findAllByCompanyIdAndClientId(UUID companyId, UUID clientId);
    void deleteByCompanyIdAndClientIdAndProductId(UUID companyId, UUID clientId, UUID productId);
}

package com.gestordevendas.api.promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ClientProductPromotionRepository extends JpaRepository<ClientProductPromotion, UUID> {
    List<ClientProductPromotion> findAllByCompany_Id(UUID companyId);
    List<ClientProductPromotion> findAllByCompany_IdAndClient_Id(UUID companyId, UUID clientId);
    Optional<ClientProductPromotion> findByCompany_IdAndClient_IdAndProduct_Id(UUID companyId, UUID clientId, UUID productId);
    void deleteByCompany_IdAndClient_IdAndProduct_Id(UUID companyId, UUID clientId, UUID productId);
}

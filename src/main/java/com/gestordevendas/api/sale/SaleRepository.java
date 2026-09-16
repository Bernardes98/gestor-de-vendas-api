package com.gestordevendas.api.sale;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
    @EntityGraph(attributePaths = {"company", "client"})
    Optional<Sale> findByIdAndCompanyId(UUID id, UUID companyId);
    @EntityGraph(attributePaths = {"company", "client"})
    List<Sale> findAllByCompanyIdOrderBySoldAtDesc(UUID companyId);
    @EntityGraph(attributePaths = {"company", "client"})
    List<Sale> findAllByCompanyIdAndStatusAndPaymentTypeOrderBySoldAtDesc(UUID companyId, SaleStatus status, SalePaymentType paymentType);

    @EntityGraph(attributePaths = {"company", "client"})
    List<Sale> findTop5ByCompanyIdAndStatusOrderBySoldAtDesc(UUID companyId, SaleStatus status);

    @EntityGraph(attributePaths = {"company", "client"})
    @Query("select s from Sale s where s.company.id=:companyId and s.status=:status and s.soldAt >= :from and s.soldAt < :to order by s.soldAt desc")
    List<Sale> findAllInRange(@Param("companyId") UUID companyId, @Param("status") SaleStatus status, @Param("from") Instant from, @Param("to") Instant to);
}

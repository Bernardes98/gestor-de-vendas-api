package com.gestordevendas.api.purchase;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @EntityGraph(attributePaths = {"company"})
    Optional<Purchase> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p from Purchase p
        where p.id = :id and p.company.id = :companyId
        """)
    Optional<Purchase> findForUpdate(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId
    );

    @EntityGraph(attributePaths = {"company"})
    List<Purchase> findAllByCompanyIdOrderByPurchasedAtDesc(UUID companyId);
}
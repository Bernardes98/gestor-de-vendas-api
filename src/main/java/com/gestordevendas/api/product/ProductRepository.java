package com.gestordevendas.api.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @EntityGraph(attributePaths = {"company", "category"})
    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.company.id = :companyId")
    Optional<Product> findForStockUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @EntityGraph(attributePaths = {"company", "category"})
    Optional<Product> findByIdAndCompanyIdAndActiveTrue(UUID id, UUID companyId);

    @EntityGraph(attributePaths = {"company", "category"})
    List<Product> findAllByCompanyIdAndActiveTrueOrderByNameAsc(UUID companyId);

    long countByCompanyIdAndCategoryId(UUID companyId, UUID categoryId);
}

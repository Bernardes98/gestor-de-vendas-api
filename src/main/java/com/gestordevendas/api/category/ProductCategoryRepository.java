package com.gestordevendas.api.category;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    @EntityGraph(attributePaths = "company")
    List<ProductCategory> findAllByCompanyIdOrderByOrderIndexAscNameAsc(UUID companyId);

    @EntityGraph(attributePaths = "company")
    Optional<ProductCategory> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);

    @Query("select coalesce(max(c.orderIndex), -1) from ProductCategory c where c.company.id = :companyId")
    int maxOrder(@Param("companyId") UUID companyId);
}

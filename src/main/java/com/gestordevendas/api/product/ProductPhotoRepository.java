package com.gestordevendas.api.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, UUID> {
    List<ProductPhoto> findAllByCompanyIdAndProductIdOrderByOrderIndexAsc(UUID companyId, UUID productId);
    Optional<ProductPhoto> findFirstByCompanyIdAndProductIdOrderByOrderIndexAsc(UUID companyId, UUID productId);

    @EntityGraph(attributePaths = {"company", "product"})
    Optional<ProductPhoto> findByIdAndCompanyIdAndProductId(UUID id, UUID companyId, UUID productId);

    @Query("select coalesce(max(p.orderIndex), -1) from ProductPhoto p where p.company.id = :companyId and p.product.id = :productId")
    int maxOrder(@Param("companyId") UUID companyId, @Param("productId") UUID productId);
}

package com.gestordevendas.api.supplier;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SupplierRepository extends JpaRepository<Supplier,UUID> {
 List<Supplier> findAllByCompanyIdOrderByOrderIndexAscNameAsc(UUID companyId);
 Optional<Supplier> findByIdAndCompanyId(UUID id, UUID companyId);
 boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId,String name);
 @Query("select coalesce(max(s.orderIndex),0) from Supplier s where s.company.id=:companyId") int maxOrder(@Param("companyId") UUID companyId);
}

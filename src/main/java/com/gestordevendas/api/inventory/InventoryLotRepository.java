package com.gestordevendas.api.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, UUID> {

 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("""
        select l
        from InventoryLot l
        where l.company.id = :companyId
          and l.product.id = :productId
          and l.remainingQuantity > 0
        order by l.entryDate asc, l.createdAt asc, l.id asc
        """)
 List<InventoryLot> fifo(
         @Param("companyId") UUID companyId,
         @Param("productId") UUID productId
 );

 @Query("""
        select l
        from InventoryLot l
        join fetch l.product p
        left join fetch l.purchase
        where l.company.id = :companyId
        order by l.entryDate asc, l.createdAt asc, l.id asc
        """)
 List<InventoryLot> allForCompany(
         @Param("companyId") UUID companyId
 );

 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("""
        select l
        from InventoryLot l
        where l.company.id = :companyId
          and l.product.id = :productId
        order by l.entryDate asc, l.createdAt asc, l.id asc
        """)
 List<InventoryLot> allForProductLocked(
         @Param("companyId") UUID companyId,
         @Param("productId") UUID productId
 );

 List<InventoryLot> findAllByCompanyIdAndPurchaseId(
         UUID companyId,
         UUID purchaseId
 );

 void deleteAllByCompanyIdAndPurchaseId(
         UUID companyId,
         UUID purchaseId
 );
}
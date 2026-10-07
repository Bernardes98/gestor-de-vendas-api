package com.gestordevendas.api.inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.util.UUID;
public interface InventoryLotRepository extends JpaRepository<InventoryLot,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from InventoryLot l join fetch l.product p left join fetch l.purchase where l.company.id=:companyId and p.id=:productId and l.remainingQuantity>0 order by l.entryDate asc,l.createdAt asc,l.id asc") List<InventoryLot> fifo(@Param("companyId") UUID companyId,@Param("productId") UUID productId);
 @Query("select l from InventoryLot l join fetch l.product p left join fetch l.purchase where l.company.id=:companyId order by p.name asc,l.entryDate asc,l.createdAt asc,l.id asc") List<InventoryLot> allForCompany(@Param("companyId") UUID companyId);
 List<InventoryLot> findAllByCompanyIdAndPurchaseId(UUID companyId,UUID purchaseId);
 void deleteAllByCompanyIdAndPurchaseId(UUID companyId,UUID purchaseId);
}

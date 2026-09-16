package com.gestordevendas.api.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    @EntityGraph(attributePaths = {"company", "client", "sale", "conversionUser"})
    Optional<CustomerOrder> findByIdAndCompanyId(UUID id, UUID companyId);

    @EntityGraph(attributePaths = {"company", "client", "sale", "conversionUser"})
    List<CustomerOrder> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    @EntityGraph(attributePaths = {"company", "client", "sale"})
    Optional<CustomerOrder> findFirstByCompanyIdAndClientIdAndStatusInOrderByCreatedAtDesc(
        UUID companyId, UUID clientId, Collection<CustomerOrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o left join fetch o.client left join fetch o.conversionUser where o.id=:id and o.company.id=:companyId")
    Optional<CustomerOrder> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);
}

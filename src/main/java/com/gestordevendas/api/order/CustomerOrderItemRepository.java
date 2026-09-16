package com.gestordevendas.api.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerOrderItemRepository extends JpaRepository<CustomerOrderItem, UUID> {
    @EntityGraph(attributePaths = "product")
    List<CustomerOrderItem> findAllByCompanyIdAndOrderIdOrderByCreatedAtAsc(UUID companyId, UUID orderId);
}

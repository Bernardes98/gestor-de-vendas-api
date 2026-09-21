package com.gestordevendas.api.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderLockRepository extends JpaRepository<CustomerOrderLock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CustomerOrderLock l join fetch l.lockedBy where l.orderId = :orderId")
    Optional<CustomerOrderLock> findForUpdate(@Param("orderId") UUID orderId);
}

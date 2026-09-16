package com.gestordevendas.api.receivable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface ManualReceivablePaymentRepository extends JpaRepository<ManualReceivablePayment, UUID> {
    @Query("select coalesce(sum(p.amount), 0) from ManualReceivablePayment p where p.company.id = :companyId and p.receivable.id = :receivableId")
    BigDecimal sumPaid(@Param("companyId") UUID companyId, @Param("receivableId") UUID receivableId);
}

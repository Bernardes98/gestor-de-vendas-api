package com.gestordevendas.api.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SalePaymentRepository extends JpaRepository<SalePayment, UUID> {
    List<SalePayment> findAllByCompanyIdAndSaleIdOrderByPaidAtAsc(UUID companyId, UUID saleId);
    @Query("select coalesce(sum(p.amount), 0) from SalePayment p where p.company.id = :companyId and p.sale.id = :saleId")
    BigDecimal sumPaid(@Param("companyId") UUID companyId, @Param("saleId") UUID saleId);
}

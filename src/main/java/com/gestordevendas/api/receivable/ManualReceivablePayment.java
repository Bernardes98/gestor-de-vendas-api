package com.gestordevendas.api.receivable;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recebivel_manual_pagamentos")
public class ManualReceivablePayment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recebivel_id", nullable = false) private ManualReceivable receivable;
    @Column(name = "valor", nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "paid_at", nullable = false) private Instant paidAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;

    protected ManualReceivablePayment() {}
    public static ManualReceivablePayment create(Company company, ManualReceivable receivable, BigDecimal amount, User createdBy) {
        ManualReceivablePayment p = new ManualReceivablePayment(); p.id = UUID.randomUUID(); p.company = company;
        p.receivable = receivable; p.amount = amount; p.paidAt = Instant.now(); p.createdBy = createdBy; return p;
    }
    public BigDecimal getAmount() { return amount; }
}

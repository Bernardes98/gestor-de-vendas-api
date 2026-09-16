package com.gestordevendas.api.sale;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "venda_pagamentos")
public class SalePayment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "venda_id", nullable = false) private Sale sale;
    @Column(name = "valor", nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "paid_at", nullable = false) private Instant paidAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;

    protected SalePayment() {}
    public static SalePayment create(Company company, Sale sale, BigDecimal amount, User user) {
        SalePayment p = new SalePayment(); p.id = UUID.randomUUID(); p.company = company; p.sale = sale; p.amount = amount;
        p.paidAt = Instant.now(); p.createdBy = user; return p;
    }
    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public Instant getPaidAt() { return paidAt; }
}

package com.gestordevendas.api.receivable;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recebivel_manual_pagamentos")
public class ManualReceivablePayment {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recebivel_id", nullable = false)
    private ManualReceivable receivable;

    @Column(name = "valor", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "data_recebimento", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "observacoes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ManualReceivablePayment() {}

    public static ManualReceivablePayment create(Company company, ManualReceivable receivable,
                                                  BigDecimal amount, LocalDate paymentDate) {
        ManualReceivablePayment p = new ManualReceivablePayment();
        p.id = UUID.randomUUID();
        p.company = company;
        p.receivable = receivable;
        p.amount = amount;
        p.paymentDate = paymentDate;
        p.createdAt = Instant.now();
        return p;
    }

    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public Instant getCreatedAt() { return createdAt; }
}

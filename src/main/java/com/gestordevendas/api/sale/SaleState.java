package com.gestordevendas.api.sale;

import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "venda_estados", schema = "api_internal")
public class SaleState {
    @Id
    @Column(name = "venda_id")
    private UUID saleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status = SaleStatus.ATIVA;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private SalePaymentType paymentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    protected SaleState() {}

    public static SaleState create(Sale sale, SalePaymentType paymentType, User createdBy) {
        SaleState state = new SaleState();
        state.saleId = sale.getId();
        state.status = SaleStatus.ATIVA;
        state.paymentType = paymentType;
        state.createdBy = createdBy;
        return state;
    }

    public UUID getSaleId() { return saleId; }
    public SaleStatus getStatus() { return status; }
    public SalePaymentType getPaymentType() { return paymentType; }
    public String getCancelReason() { return cancelReason; }
    public void setPaymentType(SalePaymentType paymentType) { this.paymentType = paymentType; }

    public void cancel(User user, String reason, Instant now) {
        this.status = SaleStatus.CANCELADA;
        this.cancelledBy = user;
        this.cancelledAt = now == null ? Instant.now() : now;
        this.cancelReason = reason == null || reason.isBlank() ? null : reason.trim();
    }
}

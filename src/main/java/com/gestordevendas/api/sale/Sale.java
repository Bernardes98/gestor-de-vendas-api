package com.gestordevendas.api.sale;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendas")
public class Sale {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @Column(name = "numero", nullable = false) private long number;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cliente_id") private Client client;
    @Column(name = "data_venda", nullable = false) private Instant soldAt;
    @Enumerated(EnumType.STRING) @Column(name = "forma_pagamento", nullable = false, length = 20) private SalePaymentType paymentType;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private SaleStatus status = SaleStatus.ATIVA;
    @Column(name = "total", nullable = false, precision = 14, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    @Column(name = "custo_total", nullable = false, precision = 14, scale = 2) private BigDecimal costTotal = BigDecimal.ZERO;
    @Column(name = "lucro_total", nullable = false, precision = 14, scale = 2) private BigDecimal profitTotal = BigDecimal.ZERO;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(name = "cancel_reason", length = 500) private String cancelReason;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cancelled_by") private User cancelledBy;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;

    protected Sale() {}
    public static Sale create(Company company, long number, Client client, Instant soldAt, SalePaymentType paymentType, User createdBy) {
        Sale sale = new Sale(); sale.id = UUID.randomUUID(); sale.company = company; sale.number = number; sale.client = client;
        sale.soldAt = soldAt == null ? Instant.now() : soldAt; sale.paymentType = paymentType; sale.createdBy = createdBy; return sale;
    }
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public long getNumber() { return number; }
    public Client getClient() { return client; }
    public Instant getSoldAt() { return soldAt; }
    public SalePaymentType getPaymentType() { return paymentType; }
    public SaleStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getCostTotal() { return costTotal; }
    public BigDecimal getProfitTotal() { return profitTotal; }
    public String getCancelReason() { return cancelReason; }
    public void update(Client client, Instant soldAt, SalePaymentType paymentType, BigDecimal total, BigDecimal costTotal) {
        this.client = client; if (soldAt != null) this.soldAt = soldAt; this.paymentType = paymentType;
        this.total = total; this.costTotal = costTotal; this.profitTotal = total.subtract(costTotal);
    }
    public void cancel(User user, String reason) {
        this.status = SaleStatus.CANCELADA; this.cancelledBy = user; this.cancelledAt = Instant.now(); this.cancelReason = reason.trim();
    }
}

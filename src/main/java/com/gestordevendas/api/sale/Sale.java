package com.gestordevendas.api.sale;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendas")
public class Sale {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Column(name = "numero_empresa", nullable = false)
    private long number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Client client;

    @Column(name = "cliente_nome")
    private String clientNameSnapshot;

    @Column(name = "data_venda", nullable = false)
    private Instant soldAt;

    @Column(name = "total_venda", nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "total_custo", nullable = false, precision = 14, scale = 2)
    private BigDecimal costTotal = BigDecimal.ZERO;

    @Column(name = "lucro", nullable = false, precision = 14, scale = 2)
    private BigDecimal profitTotal = BigDecimal.ZERO;

    @Column(name = "observacoes")
    private String notes;

    @Column(name = "status_pagamento", nullable = false)
    private String paymentStatus = "PAGO";

    @Column(name = "pago_em")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Formula("coalesce((select ve.status from api_internal.venda_estados ve where ve.venda_id = id), 'ATIVA')")
    private SaleStatus status;

    @Enumerated(EnumType.STRING)
    @Formula("coalesce((select ve.forma_pagamento from api_internal.venda_estados ve where ve.venda_id = id), case when status_pagamento = 'A_RECEBER' then 'PRAZO' else 'AVISTA' end)")
    private SalePaymentType paymentType;

    @Formula("(select ve.cancel_reason from api_internal.venda_estados ve where ve.venda_id = id)")
    private String cancelReason;

    protected Sale() {}

    public static Sale create(Company company, long number, Client client, Instant soldAt, SalePaymentType paymentType) {
        Sale sale = new Sale();
        sale.id = UUID.randomUUID();
        sale.company = company;
        sale.number = number;
        sale.client = client;
        sale.clientNameSnapshot = client == null ? null : client.getName();
        sale.soldAt = soldAt == null ? Instant.now() : soldAt;
        sale.setPaymentTypeForBusiness(paymentType);
        return sale;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public long getNumber() { return number; }
    public Client getClient() { return client; }
    public String getClientNameSnapshot() { return clientNameSnapshot; }
    public Instant getSoldAt() { return soldAt; }
    public SalePaymentType getPaymentType() { return paymentType != null ? paymentType : derivePaymentType(); }
    public SaleStatus getStatus() { return status == null ? SaleStatus.ATIVA : status; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getCostTotal() { return costTotal; }
    public BigDecimal getProfitTotal() { return profitTotal; }
    public String getCancelReason() { return cancelReason; }
    public String getPaymentStatus() { return paymentStatus; }
    public Instant getPaidAt() { return paidAt; }

    public void applyRuntimeState(SaleStatus status, SalePaymentType paymentType, String cancelReason) {
        this.status = status;
        this.paymentType = paymentType;
        this.cancelReason = cancelReason;
    }

    public void update(Client client, Instant soldAt, SalePaymentType paymentType, BigDecimal total, BigDecimal costTotal) {
        this.client = client;
        this.clientNameSnapshot = client == null ? null : client.getName();
        if (soldAt != null) this.soldAt = soldAt;
        setPaymentTypeForBusiness(paymentType);
        this.total = total;
        this.costTotal = costTotal;
        this.profitTotal = total.subtract(costTotal);
    }

    public void markPaid(Instant when) {
        this.paymentStatus = "PAGO";
        this.paidAt = when == null ? Instant.now() : when;
    }

    public void markReceivable() {
        this.paymentStatus = "A_RECEBER";
        this.paidAt = null;
    }

    private void setPaymentTypeForBusiness(SalePaymentType paymentType) {
        this.paymentType = paymentType == null ? SalePaymentType.AVISTA : paymentType;
        if (this.paymentType == SalePaymentType.PRAZO) markReceivable();
        else markPaid(this.soldAt == null ? Instant.now() : this.soldAt);
    }

    private SalePaymentType derivePaymentType() {
        return "A_RECEBER".equals(paymentStatus) ? SalePaymentType.PRAZO : SalePaymentType.AVISTA;
    }
}

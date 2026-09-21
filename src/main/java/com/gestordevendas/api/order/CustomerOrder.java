package com.gestordevendas.api.order;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.sale.Sale;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedidos_cliente")
public class CustomerOrder {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerOrderStatus status = CustomerOrderStatus.PENDENTE;

    @Column(name = "visualizado_em")
    private Instant viewedAt;

    @Column(name = "observacoes")
    private String notes;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversao_por", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User conversionUser;

    @Column(name = "conversao_em")
    private Instant conversionStartedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected CustomerOrder() {}

    public static CustomerOrder create(Company company, Client client, String notes, BigDecimal total) {
        CustomerOrder order = new CustomerOrder();
        order.id = UUID.randomUUID();
        order.company = company;
        order.client = client;
        order.notes = notes == null || notes.isBlank() ? null : notes.trim();
        order.total = total;
        return order;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Client getClient() { return client; }
    public CustomerOrderStatus getStatus() { return status; }
    public Instant getViewedAt() { return viewedAt; }
    public String getNotes() { return notes; }
    public BigDecimal getTotal() { return total; }
    public Sale getSale() { return sale; }
    public User getConversionUser() { return conversionUser; }
    public Instant getConversionStartedAt() { return conversionStartedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markViewed(Instant now) { if (viewedAt == null) viewedAt = now; }
    public void reject() { status = CustomerOrderStatus.RECUSADO; }

    public void convert(Sale sale, User user, Instant now) {
        this.sale = sale;
        this.status = CustomerOrderStatus.CONVERTIDO;
        this.conversionUser = user;
        this.conversionStartedAt = now == null ? Instant.now() : now;
    }
}

package com.gestordevendas.api.order;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.sale.Sale;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedidos_cliente")
public class CustomerOrder {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id", nullable = false) private Client client;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CustomerOrderStatus status = CustomerOrderStatus.PENDENTE;
    @Column(name = "visualizado_em") private Instant viewedAt;
    @Column(name = "observacoes", length = 1000) private String notes;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal total;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "venda_id") private Sale sale;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversao_usuario_id") private User conversionUser;
    @Column(name = "conversao_iniciada_em") private Instant conversionStartedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;

    protected CustomerOrder() {}

    public static CustomerOrder create(Company company, Client client, String notes, BigDecimal total) {
        CustomerOrder order = new CustomerOrder(); order.id = UUID.randomUUID(); order.company = company; order.client = client;
        order.notes = notes == null || notes.isBlank() ? null : notes.trim(); order.total = total; return order;
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

    public boolean claim(User user, Instant now, Duration timeout) {
        if (status != CustomerOrderStatus.PENDENTE) return false;
        if (conversionUser != null && conversionUser.getId().equals(user.getId())) {
            conversionStartedAt = now; return true;
        }
        boolean expired = conversionStartedAt == null || conversionStartedAt.isBefore(now.minus(timeout));
        if (conversionUser == null || expired) {
            conversionUser = user; conversionStartedAt = now; return true;
        }
        return false;
    }

    public void releaseClaim(UUID userId, boolean force) {
        if (force || conversionUser == null || conversionUser.getId().equals(userId)) {
            conversionUser = null; conversionStartedAt = null;
        }
    }

    public void reject() {
        status = CustomerOrderStatus.RECUSADO; conversionUser = null; conversionStartedAt = null;
    }

    public void convert(Sale sale) {
        this.sale = sale; status = CustomerOrderStatus.CONVERTIDO; conversionUser = null; conversionStartedAt = null;
    }
}

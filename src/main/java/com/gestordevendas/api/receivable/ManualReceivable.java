package com.gestordevendas.api.receivable;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recebiveis_manuais")
public class ManualReceivable {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cliente_id") private Client client;
    @Column(name = "descricao", nullable = false, length = 500) private String description;
    @Column(name = "valor_total", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private ManualReceivableStatus status = ManualReceivableStatus.ABERTO;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(name = "settled_at") private Instant settledAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;

    protected ManualReceivable() {}
    public static ManualReceivable create(Company company, Client client, String description, BigDecimal totalAmount, User createdBy) {
        ManualReceivable r = new ManualReceivable(); r.id = UUID.randomUUID(); r.company = company; r.client = client;
        r.description = description.trim(); r.totalAmount = totalAmount; r.createdBy = createdBy; return r;
    }
    public UUID getId() { return id; }
    public Client getClient() { return client; }
    public String getDescription() { return description; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public ManualReceivableStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void settle() { this.status = ManualReceivableStatus.QUITADO; this.settledAt = Instant.now(); }
}

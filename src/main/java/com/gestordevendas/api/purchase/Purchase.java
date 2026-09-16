package com.gestordevendas.api.purchase;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compras")
public class Purchase {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @Column(name = "data_compra", nullable = false) private Instant purchasedAt;
    @Column(name = "observacoes", length = 1000) private String notes;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private PurchaseStatus status = PurchaseStatus.ATIVA;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cancelled_by") private User cancelledBy;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;

    protected Purchase() {}
    public static Purchase create(Company company, User user, Instant purchasedAt, String notes) {
        Purchase p = new Purchase(); p.id = UUID.randomUUID(); p.company = company; p.createdBy = user;
        p.purchasedAt = purchasedAt == null ? Instant.now() : purchasedAt; p.setNotes(notes); return p;
    }
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public String getNotes() { return notes; }
    public PurchaseStatus getStatus() { return status; }
    public void update(Instant purchasedAt, String notes) { if (purchasedAt != null) this.purchasedAt = purchasedAt; setNotes(notes); }
    public void cancel(User user) { this.status = PurchaseStatus.CANCELADA; this.cancelledAt = Instant.now(); this.cancelledBy = user; }
    private void setNotes(String value) { this.notes = value == null || value.isBlank() ? null : value.trim(); }
}

package com.gestordevendas.api.purchase;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "compras")
public class Purchase {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Column(name = "fornecedor", length = 150)
    private String supplier;

    @Column(name = "data_compra", nullable = false)
    private LocalDate purchasedAt;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "observacoes")
    private String notes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Purchase() {}

    public static Purchase create(Company company, LocalDate purchasedAt, String notes) {
        Purchase p = new Purchase();
        p.id = UUID.randomUUID();
        p.company = company;
        p.purchasedAt = purchasedAt == null ? LocalDate.now() : purchasedAt;
        p.setNotes(notes);
        return p;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public LocalDate getPurchasedAt() { return purchasedAt; }
    public String getNotes() { return notes; }
    public BigDecimal getTotal() { return total; }
    public String getSupplier() { return supplier; }
    public void update(LocalDate purchasedAt, String notes) {
        if (purchasedAt != null) this.purchasedAt = purchasedAt;
        setNotes(notes);
    }
    public void setTotal(BigDecimal total) { this.total = total == null ? BigDecimal.ZERO : total; }
    private void setNotes(String value) { this.notes = value == null || value.isBlank() ? null : value.trim(); }
}

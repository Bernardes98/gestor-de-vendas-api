package com.gestordevendas.api.category;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "produto_grupos")
public class ProductCategory {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Column(name = "nome", nullable = false)
    private String name;

    @Column(name = "ordem", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ProductCategory() {}

    public static ProductCategory create(Company company, String name, int orderIndex) {
        ProductCategory category = new ProductCategory();
        category.id = UUID.randomUUID();
        category.company = company;
        category.name = name.trim();
        category.orderIndex = Math.max(1, orderIndex);
        return category;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getName() { return name; }
    public int getOrderIndex() { return orderIndex; }
    public void setName(String name) { this.name = name.trim(); }
    public void setOrderIndex(int orderIndex) { this.orderIndex = Math.max(1, orderIndex); }
}

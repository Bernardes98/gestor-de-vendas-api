package com.gestordevendas.api.product;

import com.gestordevendas.api.category.ProductCategory;
import com.gestordevendas.api.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "produtos")
public class Product {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private ProductCategory category;

    @Column(name = "nome", nullable = false, length = 180)
    private String name;

    @Column(name = "codigo", length = 80)
    private String code;

    @Column(name = "marca", length = 120)
    private String brand;

    @Column(name = "descricao", length = 1000)
    private String description;

    @Column(name = "custo", nullable = false, precision = 14, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "preco_venda", nullable = false, precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "controlar_estoque", nullable = false)
    private boolean stockControlled;

    @Column(name = "estoque_atual", nullable = false, precision = 14, scale = 3)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "estoque_minimo", nullable = false, precision = 14, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "ativo", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Product() {}

    public static Product create(Company company, String name, BigDecimal salePrice) {
        Product product = new Product();
        product.id = UUID.randomUUID();
        product.company = company;
        product.name = name.trim();
        product.salePrice = salePrice;
        return product;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public ProductCategory getCategory() { return category; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getBrand() { return brand; }
    public String getDescription() { return description; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public boolean isStockControlled() { return stockControlled; }
    public BigDecimal getCurrentStock() { return currentStock; }
    public BigDecimal getMinimumStock() { return minimumStock; }
    public boolean isActive() { return active; }

    public void update(String name, String code, String description, ProductCategory category,
                       BigDecimal costPrice, BigDecimal salePrice, boolean stockControlled) {
        update(name, code, null, description, category, costPrice, salePrice, stockControlled, BigDecimal.ZERO);
    }

    public void update(String name, String code, String brand, String description, ProductCategory category,
                       BigDecimal costPrice, BigDecimal salePrice, boolean stockControlled, BigDecimal minimumStock) {
        this.name = name.trim();
        this.code = blankToNull(code);
        this.brand = blankToNull(brand);
        this.description = blankToNull(description);
        this.category = category;
        this.costPrice = costPrice;
        this.salePrice = salePrice;
        this.stockControlled = stockControlled;
        this.minimumStock = minimumStock == null ? BigDecimal.ZERO : minimumStock;
    }

    public void setActive(boolean active) { this.active = active; }

    public void adjustStock(BigDecimal delta) {
        BigDecimal next = this.currentStock.add(delta);
        if (next.signum() < 0) {
            throw new IllegalArgumentException("Estoque não pode ficar negativo.");
        }
        this.currentStock = next;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

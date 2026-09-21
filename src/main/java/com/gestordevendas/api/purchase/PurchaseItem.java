package com.gestordevendas.api.purchase;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "compra_itens")
public class PurchaseItem {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Product product;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "custo_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    protected PurchaseItem() {}

    public static PurchaseItem create(Company company, Purchase purchase, Product product, BigDecimal quantity, BigDecimal unitCost) {
        PurchaseItem item = new PurchaseItem();
        item.id = UUID.randomUUID();
        item.company = company;
        item.purchase = purchase;
        item.product = product;
        item.quantity = quantity;
        item.unitCost = unitCost;
        item.total = unitCost.multiply(quantity);
        return item;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotal() { return total; }
    public boolean isStockMoved() { return product != null && product.isStockControlled(); }
}

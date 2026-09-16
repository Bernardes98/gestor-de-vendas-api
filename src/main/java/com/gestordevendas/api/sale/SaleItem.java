package com.gestordevendas.api.sale;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "venda_itens")
public class SaleItem {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "venda_id", nullable = false) private Sale sale;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "produto_id", nullable = false) private Product product;
    @Column(name = "produto_nome", nullable = false, length = 180) private String productName;
    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3) private BigDecimal quantity;
    @Column(name = "preco_unitario", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "custo_unitario", nullable = false, precision = 14, scale = 2) private BigDecimal unitCost;
    @Column(name = "total_linha", nullable = false, precision = 14, scale = 2) private BigDecimal lineTotal;
    @Column(name = "custo_linha", nullable = false, precision = 14, scale = 2) private BigDecimal lineCost;
    @Column(name = "movimenta_estoque", nullable = false) private boolean stockMoved;

    protected SaleItem() {}
    public static SaleItem create(Company company, Sale sale, Product product, BigDecimal quantity,
                                  BigDecimal unitPrice, BigDecimal unitCost, BigDecimal lineTotal, BigDecimal lineCost) {
        SaleItem item = new SaleItem(); item.id = UUID.randomUUID(); item.company = company; item.sale = sale; item.product = product;
        item.productName = product.getName(); item.quantity = quantity; item.unitPrice = unitPrice; item.unitCost = unitCost;
        item.lineTotal = lineTotal; item.lineCost = lineCost; item.stockMoved = product.isStockControlled(); return item;
    }
    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getProductName() { return productName; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public BigDecimal getLineCost() { return lineCost; }
    public boolean isStockMoved() { return stockMoved; }
}

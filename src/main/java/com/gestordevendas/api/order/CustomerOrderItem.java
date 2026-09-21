package com.gestordevendas.api.order;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedido_cliente_itens")
public class CustomerOrderItem {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pedido_id", nullable = false) private CustomerOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "produto_id", nullable = false) private Product product;
    @Column(name = "produto_nome", nullable = false) private String productName;
    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3) private BigDecimal quantity;
    @Column(name = "preco_unitario", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "total", nullable = false, precision = 14, scale = 2) private BigDecimal lineTotal;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;

    protected CustomerOrderItem() {}

    public static CustomerOrderItem create(Company company, CustomerOrder order, Product product,
                                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        CustomerOrderItem item = new CustomerOrderItem();
        item.id = UUID.randomUUID(); item.company = company; item.order = order; item.product = product;
        item.productName = product.getName(); item.quantity = quantity; item.unitPrice = unitPrice; item.lineTotal = lineTotal;
        return item;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getProductName() { return productName; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}

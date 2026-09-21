package com.gestordevendas.api.pricing;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cliente_produto_preco")
public class ClientProductPrice {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Product product;

    @Column(name = "tipo", nullable = false, length = 20)
    private String type = "preco_fixo";

    @Column(name = "taxa", precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "preco_fixo", precision = 14, scale = 2)
    private BigDecimal fixedPrice;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ClientProductPrice() {}

    public static ClientProductPrice create(Company company, Client client, Product product, BigDecimal price) {
        ClientProductPrice value = new ClientProductPrice();
        value.id = UUID.randomUUID();
        value.company = company;
        value.client = client;
        value.product = product;
        value.setPrice(price);
        return value;
    }

    public UUID getId() { return id; }
    public UUID getProductReferenceId() { return product.getId(); }
    public BigDecimal getPrice() {
        if ("preco_fixo".equals(type) && fixedPrice != null) return fixedPrice;
        if ("taxa".equals(type) && rate != null) {
            return product.getSalePrice().multiply(BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
        }
        return product.getSalePrice();
    }
    public void setPrice(BigDecimal price) {
        this.type = "preco_fixo";
        this.fixedPrice = price;
        this.rate = null;
    }
}

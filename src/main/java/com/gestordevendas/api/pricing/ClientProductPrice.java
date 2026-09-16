package com.gestordevendas.api.pricing;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
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

    @Column(name = "preco", nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ClientProductPrice() {}

    public static ClientProductPrice create(Company company, Client client, Product product, BigDecimal price) {
        ClientProductPrice value = new ClientProductPrice();
        value.id = UUID.randomUUID();
        value.company = company;
        value.client = client;
        value.product = product;
        value.price = price;
        return value;
    }

    public UUID getId() { return id; }
    public UUID getProductReferenceId() { return product.getId(); }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}

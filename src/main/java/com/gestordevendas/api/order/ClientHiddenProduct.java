package com.gestordevendas.api.order;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cliente_produto_oculto")
public class ClientHiddenProduct {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id", nullable = false) private Client client;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "produto_id", nullable = false) private Product product;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;

    protected ClientHiddenProduct() {}

    public static ClientHiddenProduct create(Company company, Client client, Product product) {
        ClientHiddenProduct value = new ClientHiddenProduct();
        value.id = UUID.randomUUID(); value.company = company; value.client = client; value.product = product;
        return value;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
}

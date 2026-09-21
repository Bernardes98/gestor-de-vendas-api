package com.gestordevendas.api.order;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "cliente_produtos_ocultos")
@IdClass(ClientHiddenProductId.class)
public class ClientHiddenProduct {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Client client;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ClientHiddenProduct() {}

    public static ClientHiddenProduct create(Company company, Client client, Product product) {
        ClientHiddenProduct value = new ClientHiddenProduct();
        value.company = company;
        value.client = client;
        value.product = product;
        return value;
    }

    public Product getProduct() { return product; }
}

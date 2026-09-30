package com.gestordevendas.api.promotion;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cliente_produto_promocao")
public class ClientProductPromotion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id", nullable = false) private Client client;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "produto_id", nullable = false) private Product product;
    @Column(name = "preco_promocional", nullable = false, precision = 14, scale = 2) private BigDecimal promotionalPrice;
    @Column(name = "quantidade_minima", nullable = false, precision = 14, scale = 3) private BigDecimal minimumQuantity;
    protected ClientProductPromotion() {}
    public static ClientProductPromotion create(Company company, Client client, Product product, BigDecimal price, BigDecimal min) {
        ClientProductPromotion v = new ClientProductPromotion(); v.id = UUID.randomUUID(); v.company = company; v.client = client; v.product = product; v.update(price, min); return v;
    }
    public UUID getProductId() { return product.getId(); }
    public Client getClient() { return client; }
    public Product getProduct() { return product; }
    public BigDecimal getPromotionalPrice() { return promotionalPrice; }
    public BigDecimal getMinimumQuantity() { return minimumQuantity; }
    public void update(BigDecimal price, BigDecimal min) { promotionalPrice = price; minimumQuantity = min; }
}

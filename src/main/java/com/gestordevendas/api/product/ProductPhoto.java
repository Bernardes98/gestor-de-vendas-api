package com.gestordevendas.api.product;

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
@Table(name = "produto_fotos")
public class ProductPhoto {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Product product;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "ordem", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ProductPhoto() {}

    public static ProductPhoto create(Company company, Product product, String objectKey,
                                      String contentType, long sizeBytes, int orderIndex) {
        ProductPhoto photo = new ProductPhoto();
        photo.id = UUID.randomUUID();
        photo.company = company;
        photo.product = product;
        photo.objectKey = objectKey;
        photo.contentType = contentType;
        photo.sizeBytes = sizeBytes;
        photo.orderIndex = orderIndex;
        return photo;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Product getProduct() { return product; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getOrderIndex() { return orderIndex; }
}

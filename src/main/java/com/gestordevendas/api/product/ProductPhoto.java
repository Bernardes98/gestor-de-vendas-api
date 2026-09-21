package com.gestordevendas.api.product;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;

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

    @Column(name = "storage_path", nullable = false, unique = true)
    private String storagePath;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "ordem", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ProductPhoto() {}

    public static ProductPhoto create(Company company, Product product, String storagePath, String url, int orderIndex) {
        ProductPhoto photo = new ProductPhoto();
        photo.id = UUID.randomUUID();
        photo.company = company;
        photo.product = product;
        photo.storagePath = storagePath;
        photo.url = url;
        photo.orderIndex = Math.max(1, orderIndex);
        return photo;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Product getProduct() { return product; }
    public String getStoragePath() { return storagePath; }
    public String getObjectKey() { return storagePath; }
    public String getUrl() { return url; }
    public String getContentType() { return null; }
    public long getSizeBytes() { return 0L; }
    public int getOrderIndex() { return orderIndex; }
}

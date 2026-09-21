package com.gestordevendas.api.receivable;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recebiveis_manuais")
public class ManualReceivable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Client client;

    @Column(name = "valor_original", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate launchDate;

    @Column(name = "observacoes")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ManualReceivableStatus status = ManualReceivableStatus.ABERTO;

    @Column(name = "quitado_em")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ManualReceivable() {}

    public static ManualReceivable create(Company company, Client client, String description,
                                           BigDecimal totalAmount, LocalDate launchDate) {
        ManualReceivable r = new ManualReceivable();
        r.id = UUID.randomUUID();
        r.company = company;
        r.client = client;
        r.description = description == null || description.isBlank() ? null : description.trim();
        r.totalAmount = totalAmount;
        r.launchDate = launchDate == null ? LocalDate.now() : launchDate;
        return r;
    }

    public UUID getId() { return id; }
    public Client getClient() { return client; }
    public String getDescription() { return description; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public ManualReceivableStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public LocalDate getLaunchDate() { return launchDate; }
    public Instant getSettledAt() { return settledAt; }

    public void settle() {
        this.status = ManualReceivableStatus.QUITADO;
        this.settledAt = Instant.now();
    }
}

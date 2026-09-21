package com.gestordevendas.api.stock;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "movimentacoes_estoque", schema = "api_internal")
public class StockMovement {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "produto_id", nullable = false) private Product product;
    @Enumerated(EnumType.STRING) @Column(name = "tipo", nullable = false, length = 30) private StockMovementType type;
    @Column(name = "quantidade_delta", nullable = false, precision = 14, scale = 3) private BigDecimal quantityDelta;
    @Column(name = "saldo_apos", nullable = false, precision = 14, scale = 3) private BigDecimal balanceAfter;
    @Column(name = "referencia_tipo", length = 30) private String referenceType;
    @Column(name = "referencia_id") private UUID referenceId;
    @Column(name = "motivo", length = 500) private String reason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;

    protected StockMovement() {}

    public static StockMovement create(Company company, Product product, StockMovementType type,
                                       BigDecimal quantityDelta, BigDecimal balanceAfter,
                                       String referenceType, UUID referenceId, String reason, User createdBy) {
        StockMovement movement = new StockMovement();
        movement.id = UUID.randomUUID();
        movement.company = company;
        movement.product = product;
        movement.type = type;
        movement.quantityDelta = quantityDelta;
        movement.balanceAfter = balanceAfter;
        movement.referenceType = referenceType;
        movement.referenceId = referenceId;
        movement.reason = reason == null || reason.isBlank() ? null : reason.trim();
        movement.createdBy = createdBy;
        return movement;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public StockMovementType getType() { return type; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}

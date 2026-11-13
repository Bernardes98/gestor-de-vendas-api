package com.gestordevendas.api.inventory;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.stock.StockMovement;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "estoque_lote_ajustes", schema = "api_internal")
public class InventoryLotAdjustment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "produto_id", nullable = false) private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lote_id", nullable = false) private InventoryLot lot;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "movimentacao_id") private StockMovement movement;
    @Column(name = "regularizacao_id") private UUID regularizationId;
    @Column(name = "origem", nullable = false, length = 40) private String origin;
    @Column(name = "quantidade_delta", nullable = false, precision = 14, scale = 3) private BigDecimal quantityDelta;
    @Column(name = "motivo", nullable = false, length = 500) private String reason;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;

    protected InventoryLotAdjustment() {}

    public static InventoryLotAdjustment manual(Company company, Product product, InventoryLot lot,
                                                StockMovement movement, BigDecimal delta, String reason) {
        if (movement == null || delta == null || delta.signum() == 0) {
            throw new IllegalArgumentException("Movimentação e quantidade não nula são obrigatórias.");
        }
        InventoryLotAdjustment adjustment = new InventoryLotAdjustment();
        adjustment.id = UUID.randomUUID();
        adjustment.company = company;
        adjustment.product = product;
        adjustment.lot = lot;
        adjustment.movement = movement;
        adjustment.origin = "AJUSTE_MANUAL";
        adjustment.quantityDelta = delta;
        adjustment.reason = reason == null || reason.isBlank() ? "Ajuste manual de estoque" : reason.trim();
        return adjustment;
    }
}

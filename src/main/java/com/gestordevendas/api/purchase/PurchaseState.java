package com.gestordevendas.api.purchase;

import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compra_estados", schema = "api_internal")
public class PurchaseState {
    @Id
    @Column(name = "compra_id")
    private UUID purchaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.ATIVA;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    protected PurchaseState() {}

    public static PurchaseState create(Purchase purchase) {
        PurchaseState state = new PurchaseState();
        state.purchaseId = purchase.getId();
        state.status = PurchaseStatus.ATIVA;
        return state;
    }

    public UUID getPurchaseId() { return purchaseId; }
    public PurchaseStatus getStatus() { return status; }
    public Instant getCancelledAt() { return cancelledAt; }
    public User getCancelledBy() { return cancelledBy; }

    public void cancel(User user, Instant now) {
        this.status = PurchaseStatus.CANCELADA;
        this.cancelledBy = user;
        this.cancelledAt = now == null ? Instant.now() : now;
    }
}

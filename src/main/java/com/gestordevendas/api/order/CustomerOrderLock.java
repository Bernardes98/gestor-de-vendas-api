package com.gestordevendas.api.order;

import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedido_locks", schema = "api_internal")
public class CustomerOrderLock {
    @Id
    @Column(name = "pedido_id")
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locked_by", nullable = false)
    private User lockedBy;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    protected CustomerOrderLock() {}

    public static CustomerOrderLock create(UUID orderId, User user, Instant now) {
        CustomerOrderLock lock = new CustomerOrderLock();
        lock.orderId = orderId;
        lock.lockedBy = user;
        lock.lockedAt = now;
        return lock;
    }

    public UUID getOrderId() { return orderId; }
    public User getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }

    public boolean expired(Instant now, Duration timeout) {
        return lockedAt == null || lockedAt.isBefore(now.minus(timeout));
    }

    public void refresh(User user, Instant now) {
        this.lockedBy = user;
        this.lockedAt = now;
    }
}

package com.gestordevendas.api.invite;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
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
@Table(name = "convites_empresa", schema = "api_internal")
public class CompanyInvite {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Column(name = "owner_email", nullable = false, length = 254)
    private String ownerEmail;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected CompanyInvite() {}

    public CompanyInvite(UUID id, Company company, String ownerEmail, String tokenHash,
                         Instant expiresAt, User createdBy) {
        this.id = id;
        this.company = company;
        this.ownerEmail = ownerEmail;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public static CompanyInvite create(Company company, String ownerEmail, String tokenHash,
                                       Instant expiresAt, User createdBy) {
        return new CompanyInvite(UUID.randomUUID(), company, ownerEmail, tokenHash, expiresAt, createdBy);
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public boolean isPending(Instant now) { return usedAt == null && cancelledAt == null && expiresAt.isAfter(now); }
    public void markUsed(Instant when) { this.usedAt = when; }
    public void cancel(Instant when) { this.cancelledAt = when; }
    public void reissue(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.cancelledAt = null;
    }
}

package com.gestordevendas.api.invite;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "convites_usuario", schema = "api_internal")
public class UserInvite {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private CompanyRole role;

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

    protected UserInvite() {}

    public UserInvite(UUID id, Company company, String email, CompanyRole role, String tokenHash,
                      Instant expiresAt, User createdBy) {
        this.id = id;
        this.company = company;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public static UserInvite create(Company company, String email, CompanyRole role, String tokenHash,
                                    Instant expiresAt, User createdBy) {
        return new UserInvite(UUID.randomUUID(), company, email, role, tokenHash, expiresAt, createdBy);
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getEmail() { return email; }
    public CompanyRole getRole() { return role; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isPending(Instant now) { return usedAt == null && cancelledAt == null && expiresAt.isAfter(now); }
    public void markUsed(Instant when) { this.usedAt = when; }
}

package com.gestordevendas.api.user;

import com.gestordevendas.api.company.Company;
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
@Table(name = "empresa_usuarios")
public class CompanyMembership {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private CompanyRole role;

    @Column(name = "ativo", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;


    protected CompanyMembership() {
    }

    public CompanyMembership(UUID id, Company company, User user, CompanyRole role, boolean active) {
        this.id = id;
        this.company = company;
        this.user = user;
        this.role = role;
        this.active = active;
    }

    public static CompanyMembership create(Company company, User user, CompanyRole role) {
        return new CompanyMembership(UUID.randomUUID(), company, user, role, true);
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public User getUser() { return user; }
    public CompanyRole getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setRole(CompanyRole role) { this.role = role; }
    public void setActive(boolean active) { this.active = active; }
}

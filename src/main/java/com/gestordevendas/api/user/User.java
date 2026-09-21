package com.gestordevendas.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "usuarios", schema = "api_internal")
public class User {
    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(length = 180)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "platform_admin", nullable = false)
    private boolean platformAdmin;

    @Column(name = "must_reset_password", nullable = false)
    private boolean mustResetPassword;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(UUID id, String email, String name, boolean active, boolean platformAdmin, boolean mustResetPassword) {
        this.id = id;
        this.email = normalizeEmail(email);
        this.nome = name;
        this.ativo = active;
        this.platformAdmin = platformAdmin;
        this.mustResetPassword = mustResetPassword;
    }

    public static User create(String email, String name, String passwordHash) {
        User user = new User(UUID.randomUUID(), email, name, true, false, false);
        user.passwordHash = passwordHash;
        return user;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return nome; }
    public boolean isActive() { return ativo; }
    public boolean isPlatformAdmin() { return platformAdmin; }
    public boolean isMustResetPassword() { return mustResetPassword; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setActive(boolean active) { this.ativo = active; }
    public void setPlatformAdmin(boolean platformAdmin) { this.platformAdmin = platformAdmin; }
    public void setMustResetPassword(boolean mustResetPassword) { this.mustResetPassword = mustResetPassword; }
    public void setName(String name) { this.nome = name; }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

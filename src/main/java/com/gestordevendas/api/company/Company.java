package com.gestordevendas.api.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "empresas")
public class Company {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "nome_fantasia", nullable = false, length = 180)
    private String name;

    @Column(name = "razao_social", length = 180)
    private String legalName;

    @Column(name = "cpf_cnpj", length = 20)
    private String document;

    @Column(length = 30)
    private String telefone;

    @Column(length = 254)
    private String email;

    @Column(length = 255)
    private String endereco;

    @Column(length = 120)
    private String cidade;

    @Column(name = "logo_url", length = 500)
    private String logoKey;

    @Column(name = "cor_primaria", nullable = false, length = 20)
    private String primaryColor = "#f59e0b";

    @Column(name = "cor_secundaria", nullable = false, length = 20)
    private String secondaryColor = "#101827";

    @Column(name = "ativa", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Company() {
    }

    public Company(UUID id, String slug, String name, boolean active) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.active = active;
    }

    public static Company create(String slug, String name, String legalName, String document,
                                 String primaryColor, String secondaryColor) {
        Company company = new Company(UUID.randomUUID(), slug, name, true);
        company.legalName = legalName;
        company.document = document;
        if (primaryColor != null && !primaryColor.isBlank()) company.primaryColor = primaryColor;
        if (secondaryColor != null && !secondaryColor.isBlank()) company.secondaryColor = secondaryColor;
        return company;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getDocument() { return document; }
    public String getPhone() { return telefone; }
    public String getEmail() { return email; }
    public String getAddress() { return endereco; }
    public String getCity() { return cidade; }
    public String getPrimaryColor() { return primaryColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public String getLogoKey() { return logoKey; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public void setLogoKey(String logoKey) { this.logoKey = logoKey; }
}

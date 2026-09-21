package com.gestordevendas.api.client;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clientes")
public class Client {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Column(name = "nome", nullable = false, length = 150)
    private String name;

    @Column(name = "razao_social", length = 150)
    private String legalName;

    @Column(name = "cpf_cnpj", length = 30)
    private String document;

    @Column(name = "telefone", length = 30)
    private String phone;

    @Column(name = "whatsapp", length = 30)
    private String whatsapp;

    @Column(name = "endereco")
    private String address;

    @Column(name = "cidade", length = 100)
    private String city;

    @Column(name = "taxa_padrao", precision = 10, scale = 4)
    private BigDecimal defaultRate = BigDecimal.ZERO;

    @Column(name = "observacoes")
    private String notes;

    @Column(name = "pedido_token", nullable = false, unique = true)
    private UUID orderToken;

    @Column(name = "ativo", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Client() {}

    public static Client create(Company company, String name) {
        Client client = new Client();
        client.id = UUID.randomUUID();
        client.company = company;
        client.name = name.trim();
        client.orderToken = UUID.randomUUID();
        return client;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getDocument() { return document; }
    public String getPhone() { return phone; }
    public String getWhatsapp() { return whatsapp; }
    public String getEmail() { return null; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public BigDecimal getDefaultRate() { return defaultRate == null ? BigDecimal.ZERO : defaultRate; }
    public String getNotes() { return notes; }
    public UUID getOrderTokenValue() { return orderToken; }
    public String getOrderToken() { return orderToken == null ? null : orderToken.toString(); }
    public boolean isActive() { return active; }

    public void update(String name, String document, String phone, String email, String address, String city, String notes) {
        this.name = name.trim();
        this.document = blankToNull(document);
        this.phone = blankToNull(phone);
        this.address = blankToNull(address);
        this.city = blankToNull(city);
        this.notes = blankToNull(notes);
    }

    public void setActive(boolean active) { this.active = active; }
    public void rotateOrderToken(UUID token) { this.orderToken = token == null ? UUID.randomUUID() : token; }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

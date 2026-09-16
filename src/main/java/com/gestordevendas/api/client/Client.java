package com.gestordevendas.api.client;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(name = "clientes")
public class Client {
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @Column(name = "nome", nullable = false, length = 180)
    private String name;

    @Column(name = "cpf_cnpj", length = 20)
    private String document;

    @Column(name = "telefone", length = 30)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(name = "endereco", length = 255)
    private String address;

    @Column(name = "cidade", length = 120)
    private String city;

    @Column(name = "observacoes", length = 1000)
    private String notes;

    @Column(name = "pedido_token", nullable = false, unique = true, length = 128)
    private String orderToken;

    @Column(name = "ativo", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Client() {}

    public static Client create(Company company, String name) {
        Client client = new Client();
        client.id = UUID.randomUUID();
        client.company = company;
        client.name = name.trim();
        client.orderToken = generateOrderToken();
        return client;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getName() { return name; }
    public String getDocument() { return document; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getNotes() { return notes; }
    public String getOrderToken() { return orderToken; }
    public boolean isActive() { return active; }

    public void update(String name, String document, String phone, String email, String address, String city, String notes) {
        this.name = name.trim();
        this.document = blankToNull(document);
        this.phone = blankToNull(phone);
        this.email = blankToNull(email);
        this.address = blankToNull(address);
        this.city = blankToNull(city);
        this.notes = blankToNull(notes);
    }

    public void setActive(boolean active) { this.active = active; }
    public void rotateOrderToken(String token) { this.orderToken = token; }

    private static String generateOrderToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

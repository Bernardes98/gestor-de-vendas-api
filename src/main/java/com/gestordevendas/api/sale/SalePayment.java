package com.gestordevendas.api.sale;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "venda_recebimentos")
public class SalePayment {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Sale sale;

    @Column(name = "valor", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "data_recebimento", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "observacoes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SalePayment() {}

    public static SalePayment create(Company company, Sale sale, BigDecimal amount, User user, LocalDate paymentDate) {
        SalePayment p = new SalePayment();
        p.id = UUID.randomUUID();
        p.company = company;
        p.sale = sale;
        p.amount = amount;
        p.paymentDate = paymentDate;
        p.createdBy = user;
        p.createdAt = Instant.now();
        return p;
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public Instant getPaidAt() { return createdAt; }
}

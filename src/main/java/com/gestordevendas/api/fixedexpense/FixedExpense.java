package com.gestordevendas.api.fixedexpense;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fixed_expenses")
public class FixedExpense {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @Column(name = "name", nullable = false, length = 120) private String name;
    @Column(name = "amount", nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "due_day") private Integer dueDay;
    @Column(name = "notes", length = 500) private String notes;

    protected FixedExpense() {}
    public static FixedExpense create(Company company, String name, BigDecimal amount, Integer dueDay, String notes) {
        var e = new FixedExpense(); e.id = UUID.randomUUID(); e.company = company; e.update(name, amount, dueDay, notes); return e;
    }
    public void update(String name, BigDecimal amount, Integer dueDay, String notes) { this.name=name; this.amount=amount; this.dueDay=dueDay; this.notes=notes; }
    public UUID getId(){return id;} public String getName(){return name;} public BigDecimal getAmount(){return amount;} public Integer getDueDay(){return dueDay;} public String getNotes(){return notes;}
}

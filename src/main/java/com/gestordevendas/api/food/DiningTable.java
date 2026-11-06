package com.gestordevendas.api.food;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "dining_tables")
public class DiningTable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "label", nullable = false, length = 80)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected DiningTable() { }

    public DiningTable(Company company, int number, String name) {
        this.id = UUID.randomUUID();
        this.company = company;
        this.number = number;
        this.name = name;
        this.active = true;
    }

    public UUID getId() { return id; }
    public int getNumber() { return number; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public void rename(String name) { this.name = name; }
    public void deactivate() { this.active = false; }
}

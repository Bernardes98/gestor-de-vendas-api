package com.gestordevendas.api.supplier;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fornecedores", uniqueConstraints = @UniqueConstraint(name="uk_fornecedor_empresa_nome", columnNames={"empresa_id","nome"}))
public class Supplier {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="empresa_id", nullable=false) private Company company;
    @Column(name="nome", nullable=false, length=150) private String name;
    @Column(name="ordem", nullable=false) private int orderIndex;
    @Column(name="created_at", nullable=false, insertable=false, updatable=false) private Instant createdAt;
    protected Supplier() {}
    public static Supplier create(Company company, String name, int orderIndex) { Supplier s=new Supplier(); s.id=UUID.randomUUID(); s.company=company; s.name=name.trim(); s.orderIndex=Math.max(1,orderIndex); return s; }
    public UUID getId(){return id;} public Company getCompany(){return company;} public String getName(){return name;} public int getOrderIndex(){return orderIndex;}
    public void setName(String name){this.name=name.trim();} public void setOrderIndex(int value){this.orderIndex=Math.max(1,value);}
}

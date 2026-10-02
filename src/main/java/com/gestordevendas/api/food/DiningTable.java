package com.gestordevendas.api.food;
import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="dining_tables") public class DiningTable {
 @Id private UUID id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @Column(name="number",nullable=false) private Integer number;
 protected DiningTable(){} public DiningTable(Company company,int number){this.id=UUID.randomUUID();this.company=company;this.number=number;}
 public UUID getId(){return id;} public int getNumber(){return number;}
}

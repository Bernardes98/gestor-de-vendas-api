package com.gestordevendas.api.inventory;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.purchase.Purchase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="estoque_lotes", schema="api_internal")
public class InventoryLot {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Company company;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="produto_id") private Product product;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="compra_id") private Purchase purchase;
 @Column(name="quantidade_inicial",nullable=false) private BigDecimal initialQuantity;
 @Column(name="quantidade_restante",nullable=false) private BigDecimal remainingQuantity;
 @Column(name="custo_unitario",nullable=false) private BigDecimal unitCost;
 @Column(name="data_entrada",nullable=false) private LocalDate entryDate;
 @Column(name="created_at",nullable=false,insertable=false,updatable=false) private Instant createdAt;
 protected InventoryLot(){}
 public static InventoryLot create(Company c, Product p, Purchase purchase, BigDecimal qty, BigDecimal cost, LocalDate date){ InventoryLot x=new InventoryLot(); x.id=UUID.randomUUID();x.company=c;x.product=p;x.purchase=purchase;x.initialQuantity=qty;x.remainingQuantity=qty;x.unitCost=cost;x.entryDate=date;return x;}
 public UUID getId(){return id;} public Product getProduct(){return product;} public Purchase getPurchase(){return purchase;} public BigDecimal getInitialQuantity(){return initialQuantity;} public BigDecimal getRemainingQuantity(){return remainingQuantity;} public BigDecimal getUnitCost(){return unitCost;} public LocalDate getEntryDate(){return entryDate;} public Instant getCreatedAt(){return createdAt;}
 public void consume(BigDecimal q){ if(q.signum()<=0||remainingQuantity.compareTo(q)<0) throw new IllegalArgumentException("Quantidade inválida no lote."); remainingQuantity=remainingQuantity.subtract(q);}
 public void restore(BigDecimal q){ remainingQuantity=remainingQuantity.add(q); if(remainingQuantity.compareTo(initialQuantity)>0) throw new IllegalArgumentException("Estorno excede o lote.");}
 public boolean consumed(){return remainingQuantity.compareTo(initialQuantity)<0;}
}

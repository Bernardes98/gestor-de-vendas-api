package com.gestordevendas.api.inventory;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.purchase.Purchase;
import com.gestordevendas.api.sale.Sale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class InventoryLotService {

 private final InventoryLotRepository lots;
 private final SaleLotConsumptionRepository consumptions;

 public InventoryLotService(
         InventoryLotRepository lots,
         SaleLotConsumptionRepository consumptions
 ) {
  this.lots = lots;
  this.consumptions = consumptions;
 }

 public void addPurchase(
         Company company,
         Purchase purchase,
         Product product,
         BigDecimal quantity,
         BigDecimal cost
 ) {
  lots.save(
          InventoryLot.create(
                  company,
                  product,
                  purchase,
                  quantity,
                  cost,
                  purchase.getPurchasedAt()
          )
  );

 }

 public void assertPurchaseUntouched(UUID companyId, UUID purchaseId) {
  boolean consumed = lots
          .findAllByCompanyIdAndPurchaseId(companyId, purchaseId)
          .stream()
          .anyMatch(InventoryLot::consumed);

  if (consumed) {
   throw new ApiException(
           HttpStatus.CONFLICT,
           "PURCHASE_LOT_ALREADY_CONSUMED",
           "Esta compra já teve unidades vendidas. Para preservar o FIFO, ela não pode ser editada ou excluída."
   );
  }
 }

 public void removePurchase(UUID companyId, UUID purchaseId) {
  var oldLots = lots.findAllByCompanyIdAndPurchaseId(
          companyId,
          purchaseId
  );

  lots.deleteAll(oldLots);
  lots.flush();

  oldLots.stream()
          .map(InventoryLot::getProduct)
          .distinct()
          .forEach(product -> syncCost(companyId, product));
 }

 public BigDecimal fifoUnitCost(
         Company company,
         Product product,
         BigDecimal quantity
 ) {
  ensureCoverage(company, product, quantity);

  BigDecimal remaining = quantity;
  BigDecimal totalCost = BigDecimal.ZERO;

  for (var lot : lots.fifo(company.getId(), product.getId())) {
   BigDecimal take = remaining.min(
           lot.getRemainingQuantity()
   );

   totalCost = totalCost.add(
           take.multiply(lot.getUnitCost())
   );

   remaining = remaining.subtract(take);

   if (remaining.signum() == 0) {
    break;
   }
  }

  if (remaining.signum() > 0) {
   throw insufficientStock(product);
  }

  return totalCost.divide(
          quantity,
          2,
          RoundingMode.HALF_UP
  );
 }

 public void consume(
         Company company,
         Sale sale,
         Product product,
         BigDecimal quantity
 ) {
  ensureCoverage(company, product, quantity);

  BigDecimal remaining = quantity;

  for (var lot : lots.fifo(company.getId(), product.getId())) {
   BigDecimal take = remaining.min(
           lot.getRemainingQuantity()
   );

   if (take.signum() > 0) {
    lot.consume(take);

    consumptions.save(
            SaleLotConsumption.create(
                    company,
                    sale,
                    product,
                    lot,
                    take
            )
    );

    remaining = remaining.subtract(take);
   }

   if (remaining.signum() == 0) {
    break;
   }
  }

  if (remaining.signum() > 0) {
   throw insufficientStock(product);
  }


 }

 public void releaseSale(UUID companyId, UUID saleId) {
  var saleConsumptions =
          consumptions.findAllByCompanyIdAndSaleId(
                  companyId,
                  saleId
          );

  var products = new LinkedHashSet<Product>();

  for (var consumption : saleConsumptions) {
   consumption
           .getLot()
           .restore(consumption.getQuantity());

   products.add(
           consumption.getLot().getProduct()
   );
  }

  consumptions.deleteAll(saleConsumptions);
  consumptions.flush();


 }

 private void ensureCoverage(
         Company company,
         Product product,
         BigDecimal needed
 ) {
  BigDecimal available = lots
          .fifo(company.getId(), product.getId())
          .stream()
          .map(InventoryLot::getRemainingQuantity)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

  if (available.compareTo(needed) >= 0) {
   return;
  }

  BigDecimal missing =
          product.getCurrentStock().subtract(available);

  if (missing.signum() > 0) {
   lots.save(
           InventoryLot.create(
                   company,
                   product,
                   null,
                   missing,
                   product.getCostPrice(),
                   LocalDate.now().minusDays(1)
           )
   );
  }
 }

 public void syncCost(UUID companyId, Product product) {
  lots.allForCompany(companyId).stream()
          .filter(lot -> lot.getProduct().getId().equals(product.getId()))
          .max(Comparator.comparing(InventoryLot::getEntryDate)
                  .thenComparing(lot -> lot.getCreatedAt() == null ? java.time.Instant.EPOCH : lot.getCreatedAt())
                  .thenComparing(InventoryLot::getId))
          .ifPresent(lot -> product.applyPurchaseCost(lot.getUnitCost()));
 }

 public List<InventoryProductResponse> summary(UUID companyId) {
  var grouped =
          new LinkedHashMap<UUID, List<InventoryLot>>();

  for (var lot : lots.allForCompany(companyId)) {
   if (lot.getRemainingQuantity().signum() > 0) {
    grouped
            .computeIfAbsent(
                    lot.getProduct().getId(),
                    key -> new ArrayList<>()
            )
            .add(lot);
   }
  }

  var response =
          new ArrayList<InventoryProductResponse>();

  for (var inventoryLots : grouped.values()) {
   var product =
           inventoryLots.get(0).getProduct();

   var layers = inventoryLots.stream()
           .map(lot ->
                   new InventoryProductResponse.Layer(
                           lot.getId(),
                           lot.getEntryDate(),
                           supplier(lot.getPurchase()),
                           lot.getInitialQuantity(),
                           lot.getRemainingQuantity(),
                           lot.getUnitCost(),
                           money(
                                   lot.getRemainingQuantity()
                                           .multiply(
                                                   lot.getUnitCost()
                                           )
                           )
                   )
           )
           .toList();

   BigDecimal quantity = inventoryLots.stream()
           .map(InventoryLot::getRemainingQuantity)
           .reduce(
                   BigDecimal.ZERO,
                   BigDecimal::add
           );

   BigDecimal value = inventoryLots.stream()
           .map(lot ->
                   lot.getRemainingQuantity()
                           .multiply(lot.getUnitCost())
           )
           .reduce(
                   BigDecimal.ZERO,
                   BigDecimal::add
           );

   response.add(
           new InventoryProductResponse(
                   product.getId(),
                   product.getName(),
                   quantity,
                   product.getCostPrice(),
                   money(value),
                   layers
           )
   );
  }

  return response;
 }

 private ApiException insufficientStock(Product product) {
  return new ApiException(
          HttpStatus.CONFLICT,
          "INSUFFICIENT_STOCK",
          "Estoque insuficiente para o produto "
                  + product.getName()
                  + "."
  );
 }

 private String supplier(Purchase purchase) {
  if (purchase == null) {
   return "Estoque anterior";
  }

  String notes = purchase.getNotes();

  if (notes != null && notes.startsWith("[Fornecedor:")) {
   int end = notes.indexOf(']');

   if (end > 12) {
    return notes.substring(12, end).trim();
   }
  }

  return purchase.getSupplier() == null
          || purchase.getSupplier().isBlank()
          ? "Compra"
          : purchase.getSupplier();
 }

 private BigDecimal money(BigDecimal value) {
  return value.setScale(
          2,
          RoundingMode.HALF_UP
  );
 }
}
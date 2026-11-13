package com.gestordevendas.api.inventory;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.purchase.Purchase;
import com.gestordevendas.api.sale.Sale;
import com.gestordevendas.api.stock.StockMovement;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class InventoryLotService {

 private final InventoryLotRepository lots;
 private final SaleLotConsumptionRepository consumptions;
 private final InventoryLotAdjustmentRepository adjustments;
 private final ProductRepository productRepository;

 public InventoryLotService(
         InventoryLotRepository lots,
         SaleLotConsumptionRepository consumptions,
         InventoryLotAdjustmentRepository adjustments,
         ProductRepository productRepository
 ) {
  this.lots = lots;
  this.consumptions = consumptions;
  this.adjustments = adjustments;
  this.productRepository = productRepository;
 }

 @Transactional(propagation = Propagation.MANDATORY)
 public void lockProducts(UUID companyId, Collection<UUID> productIds) {
  productIds.stream()
          .distinct()
          .sorted()
          .forEach(productId -> productRepository.findForStockUpdate(productId, companyId)
                  .orElseThrow(() -> new ApiException(
                          HttpStatus.NOT_FOUND,
                          "PRODUCT_NOT_FOUND",
                          "Produto não encontrado."
                  )));
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
   syncCost(company.getId(), product);

 }

 @Transactional(propagation = Propagation.MANDATORY)
 public void assertPurchaseUntouched(UUID companyId, UUID purchaseId) {
  boolean consumed = lots
          .findPurchaseLotsLocked(companyId, purchaseId)
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

 @Transactional(propagation = Propagation.MANDATORY)
 public void removePurchase(UUID companyId, UUID purchaseId) {
  var oldLots = lots.findPurchaseLotsLocked(
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

 @Transactional
 public BigDecimal fifoUnitCost(
         Company company,
         Product product,
         BigDecimal quantity
 ) {
  requirePositive(quantity);
  var availableLots = lots.fifo(company.getId(), product.getId());
  ensureCoverage(product, quantity, availableLots);

  BigDecimal remaining = quantity;
  BigDecimal totalCost = BigDecimal.ZERO;

  for (var lot : availableLots) {
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

 @Transactional
 public BigDecimal consumeAndGetCost(
         Company company, Sale sale, Product product, BigDecimal quantity
 ) {
  requirePositive(quantity);
  var availableLots = lots.fifo(company.getId(), product.getId());
  ensureCoverage(product, quantity, availableLots);
  BigDecimal remaining = quantity;
  BigDecimal totalCost = BigDecimal.ZERO;
  for (var lot : availableLots) {
   BigDecimal take = remaining.min(lot.getRemainingQuantity());
   if (take.signum() > 0) {
    totalCost = totalCost.add(take.multiply(lot.getUnitCost()));
    lot.consume(take);
    consumptions.save(SaleLotConsumption.create(company, sale, product, lot, take));
    remaining = remaining.subtract(take);
   }
   if (remaining.signum() == 0) break;
  }
  if (remaining.signum() > 0) throw insufficientStock(product);
  syncCost(company.getId(), product);
  return totalCost.setScale(2, RoundingMode.HALF_UP);
 }

 @Transactional
 public void consume(Company company, Sale sale, Product product, BigDecimal quantity) {
  consumeAndGetCost(company, sale, product, quantity);
 }

 /**
  * Restores the tracked quantities, returning the per-product total restored.
  * Old pre-FIFO sales can have items without a corresponding consumption row;
  * callers must not consume those quantities again on an edit.
  */
 @Transactional
 public Map<UUID, BigDecimal> releaseSale(UUID companyId, UUID saleId) {
  var saleConsumptions = consumptions.findAllByCompanyIdAndSaleId(companyId, saleId);
  var products = new LinkedHashSet<Product>();
  var trackedByProduct = new HashMap<UUID, BigDecimal>();

  // Lock every affected product's lots, including fully consumed lots.
  // FIFO's regular query excludes zero-balance lots, which are precisely
  // the ones that may need restoration during cancellation.
  var productIds = saleConsumptions.stream()
          .map(c -> c.getLot().getProduct().getId()).distinct().sorted().toList();
  var lockedLots = new HashMap<UUID, InventoryLot>();
  for (UUID productId : productIds) {
   for (var lot : lots.allForProductLocked(companyId, productId)) {
    lockedLots.put(lot.getId(), lot);
   }
  }
  for (var consumption : saleConsumptions) {
   var lot = lockedLots.get(consumption.getLot().getId());
   if (lot == null) {
    throw new ApiException(HttpStatus.CONFLICT, "FIFO_LOT_MISSING",
            "Lote de estoque não encontrado para estorno da venda.");
   }
   lot.restore(consumption.getQuantity());
   Product product = lot.getProduct();
   products.add(product);
   trackedByProduct.merge(product.getId(), consumption.getQuantity(), BigDecimal::add);
  }

  consumptions.deleteAll(saleConsumptions);
  consumptions.flush();
  products.forEach(product -> syncCost(companyId, product));
  return trackedByProduct;
 }

 /**
  * A manual adjustment must change the FIFO quantities within the same
  * transaction as the product balance and its stock movement.
  */
 @Transactional
 public void applyManualAdjustment(Company company, Product product, BigDecimal delta,
                                   StockMovement movement, String reason) {
  Objects.requireNonNull(delta, "delta");
  Objects.requireNonNull(movement, "movement");
  if (delta.signum() == 0) throw new IllegalArgumentException("Ajuste de estoque não pode ser zero.");
  if (delta.signum() > 0) {
   InventoryLot lot = lots.save(InventoryLot.create(
           company, product, null, delta, product.getCostPrice(), LocalDate.now()));
   adjustments.save(InventoryLotAdjustment.manual(company, product, lot, movement, delta, reason));
  } else {
   BigDecimal remaining = delta.negate();
   for (var lot : lots.fifo(company.getId(), product.getId())) {
    BigDecimal take = remaining.min(lot.getRemainingQuantity());
    if (take.signum() > 0) {
     lot.consume(take);
     adjustments.save(InventoryLotAdjustment.manual(company, product, lot, movement, take.negate(), reason));
     remaining = remaining.subtract(take);
    }
    if (remaining.signum() == 0) break;
   }
   if (remaining.signum() > 0) throw insufficientStock(product);
  }
  syncCost(company.getId(), product);
 }

 /**
  * Quantity returned from a sale that predates FIFO migration: no lot was
  * consumed originally, so the return requires a new, traceable stock layer.
  */
 @Transactional
 public void returnFromLegacySale(Company company, Product product, BigDecimal quantity, BigDecimal cost) {
  Objects.requireNonNull(quantity, "quantity");
  if (quantity.signum() <= 0) return;
  lots.save(InventoryLot.create(
          company, product, null, quantity, cost, LocalDate.now()
  ));
  syncCost(company.getId(), product);
 }

 private void requirePositive(BigDecimal quantity) {
  if (quantity == null || quantity.signum() <= 0) {
   throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FIFO_QUANTITY",
           "A quantidade para movimentação FIFO deve ser maior que zero.");
  }
 }

 private void ensureCoverage(Product product, BigDecimal needed, List<InventoryLot> availableLots) {
  BigDecimal available = availableLots.stream()
          .map(InventoryLot::getRemainingQuantity)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
  if (available.compareTo(needed) < 0) {
   // Preserve the normal insufficient-stock error when both the product
   // balance and the FIFO balance are below the requested quantity.
   // During a sale edit the previously consumed lots can already have
   // been restored, so do not reject based on product stock alone.
   if (product.getCurrentStock().compareTo(needed) < 0) {
    throw insufficientStock(product);
   }
   // The product balance covers the request, but its FIFO lots do not.
   // Never create a synthetic layer to hide this discrepancy.
   throw new ApiException(HttpStatus.CONFLICT, "FIFO_STOCK_MISMATCH",
           "O saldo FIFO do produto " + product.getName()
                   + " precisa ser conciliado antes desta operação.");
  }
 }

 public void syncCost(UUID companyId, Product product) {
  // Exibe o custo do primeiro lote FIFO com saldo disponível.
  lots.fifo(companyId, product.getId()).stream()
      .filter(lot -> lot.getRemainingQuantity().signum() > 0)
      .findFirst()
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
package com.gestordevendas.api.purchase;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.report.BusinessTimeProperties;
import com.gestordevendas.api.stock.StockMovementType;
import com.gestordevendas.api.stock.StockService;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PurchaseService {
    private final PurchaseRepository repository;
    private final PurchaseItemRepository itemRepository;
    private final PurchaseStateRepository stateRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final AuditService auditService;
    private final ZoneId businessZone;

    public PurchaseService(PurchaseRepository repository, PurchaseItemRepository itemRepository,
                           PurchaseStateRepository stateRepository, ProductRepository productRepository,
                           CompanyRepository companyRepository, UserRepository userRepository, StockService stockService,
                           CurrentUserService currentUserService, TenantContextService tenantContextService,
                           TenantGuard tenantGuard, AuditService auditService, BusinessTimeProperties businessTimeProperties) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.stateRepository = stateRepository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
        this.auditService = auditService;
        this.businessZone = ZoneId.of(businessTimeProperties.effectiveZoneId());
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> list() {
        TenantContext context = adminContext();
        return repository.findAllByCompanyIdOrderByPurchasedAtDesc(context.companyId()).stream()
            .map(p -> response(p, context.companyId())).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(UUID id) {
        TenantContext context = adminContext();
        return response(require(id, context.companyId()), context.companyId());
    }

    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        TenantContext context = adminContext();
        validateUniqueProducts(request.items());
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        Purchase purchase = repository.save(Purchase.create(company, toDate(request.purchasedAt()), request.notes()));
        stateRepository.save(PurchaseState.create(purchase));
        List<PurchaseItem> items = buildItems(request.items(), context, company, purchase, true);
        itemRepository.saveAll(items);
        purchase.setTotal(total(items));
        for (PurchaseItem item : items.stream().filter(PurchaseItem::isStockMoved).sorted(Comparator.comparing(i -> i.getProduct().getId())).toList()) {
            stockService.applyDelta(context, item.getProduct(), item.getQuantity(), StockMovementType.COMPRA,
                "COMPRA", purchase.getId(), null, "INSUFFICIENT_STOCK", "Estoque insuficiente.");
        }
        auditService.record("PURCHASE_CREATED", context.companyId(), context.userId(), "PURCHASE", purchase.getId(), null, Map.of());
        return response(purchase, items);
    }

    @Transactional
    public PurchaseResponse update(UUID id, PurchaseRequest request) {
        TenantContext context = adminContext();
        validateUniqueProducts(request.items());
        Purchase purchase = requireActive(id, context.companyId());
        List<PurchaseItem> oldItems = itemRepository.findAllByCompanyIdAndPurchaseId(context.companyId(), id);
        Map<UUID, BigDecimal> oldQty = stockQuantities(oldItems);
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        List<PurchaseItem> newItems = buildItems(request.items(), context, company, purchase, true);
        Map<UUID, BigDecimal> newQty = stockQuantities(newItems);
        Map<UUID, Product> products = new HashMap<>();
        oldItems.forEach(i -> products.put(i.getProduct().getId(), i.getProduct()));
        newItems.forEach(i -> products.put(i.getProduct().getId(), i.getProduct()));
        Set<UUID> ids = new HashSet<>(oldQty.keySet());
        ids.addAll(newQty.keySet());
        for (UUID productId : ids.stream().sorted().toList()) {
            BigDecimal delta = newQty.getOrDefault(productId, BigDecimal.ZERO).subtract(oldQty.getOrDefault(productId, BigDecimal.ZERO));
            if (delta.signum() != 0) {
                stockService.applyDelta(context, products.get(productId), delta,
                    delta.signum() > 0 ? StockMovementType.COMPRA : StockMovementType.COMPRA_REVERSAO,
                    "COMPRA", id, "Edição de compra", "STOCK_REVERSAL_NOT_ALLOWED",
                    "Não é possível reverter a compra porque o estoque ficaria negativo.");
            }
        }
        itemRepository.deleteAllByCompanyIdAndPurchaseId(context.companyId(), id);
        itemRepository.flush();
        itemRepository.saveAll(newItems);
        purchase.update(toDate(request.purchasedAt()), request.notes());
        purchase.setTotal(total(newItems));
        auditService.record("PURCHASE_UPDATED", context.companyId(), context.userId(), "PURCHASE", purchase.getId(), null, Map.of());
        return response(purchase, newItems);
    }

    @Transactional
    public void cancel(UUID id) {
        TenantContext context = adminContext();
        Purchase purchase = requireActive(id, context.companyId());
        List<PurchaseItem> items = itemRepository.findAllByCompanyIdAndPurchaseId(context.companyId(), id);
        for (PurchaseItem item : items.stream().filter(PurchaseItem::isStockMoved).sorted(Comparator.comparing(i -> i.getProduct().getId())).toList()) {
            stockService.applyDelta(context, item.getProduct(), item.getQuantity().negate(), StockMovementType.COMPRA_REVERSAO,
                "COMPRA", id, "Cancelamento de compra", "STOCK_REVERSAL_NOT_ALLOWED",
                "Não é possível cancelar a compra porque o estoque ficaria negativo.");
        }
        User user = userRepository.findById(context.userId()).orElseThrow();
        PurchaseState state = stateRepository.findById(id).orElseGet(() -> PurchaseState.create(purchase));
        state.cancel(user, Instant.now());
        stateRepository.save(state);
        auditService.record("PURCHASE_CANCELLED", context.companyId(), context.userId(), "PURCHASE", purchase.getId(), null, Map.of());
    }

    private List<PurchaseItem> buildItems(List<PurchaseItemRequest> requests, TenantContext context,
                                          Company company, Purchase purchase, boolean requireActive) {
        return requests.stream().map(r -> {
            Product product = requireActive
                ? productRepository.findByIdAndCompanyIdAndActiveTrue(r.productId(), context.companyId()).orElseThrow(this::productNotFound)
                : productRepository.findByIdAndCompanyId(r.productId(), context.companyId()).orElseThrow(this::productNotFound);
            return PurchaseItem.create(company, purchase, product, r.quantity(), r.unitCost());
        }).toList();
    }

    private Map<UUID, BigDecimal> stockQuantities(List<PurchaseItem> items) {
        return items.stream().filter(PurchaseItem::isStockMoved)
            .collect(Collectors.toMap(i -> i.getProduct().getId(), PurchaseItem::getQuantity, BigDecimal::add));
    }

    private void validateUniqueProducts(List<PurchaseItemRequest> items) {
        long distinct = items.stream().map(PurchaseItemRequest::productId).distinct().count();
        if (distinct != items.size()) throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT", "Produto duplicado na compra.");
    }

    private Purchase require(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND", "Compra não encontrada."));
    }

    private Purchase requireActive(UUID id, UUID companyId) {
        Purchase p = require(id, companyId);
        PurchaseStatus status = stateRepository.findById(id).map(PurchaseState::getStatus).orElse(PurchaseStatus.ATIVA);
        if (status == PurchaseStatus.CANCELADA) {
            throw new ApiException(HttpStatus.CONFLICT, "PURCHASE_CANCELLED", "Compra já cancelada.");
        }
        return p;
    }

    private ApiException productNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado.");
    }

    private PurchaseResponse response(Purchase p, UUID companyId) {
        return response(p, itemRepository.findAllByCompanyIdAndPurchaseId(companyId, p.getId()));
    }

    private PurchaseResponse response(Purchase p, List<PurchaseItem> items) {
        PurchaseStatus status = stateRepository.findById(p.getId()).map(PurchaseState::getStatus).orElse(PurchaseStatus.ATIVA);
        return new PurchaseResponse(p.getId(), toInstant(p.getPurchasedAt()), p.getNotes(), status, total(items),
            items.stream().map(i -> new PurchaseItemResponse(i.getProduct().getId(), i.getProduct().getName(), i.getQuantity(), i.getUnitCost())).toList());
    }

    private BigDecimal total(List<PurchaseItem> items) {
        return items.stream().map(PurchaseItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate toDate(Instant instant) {
        return instant == null ? LocalDate.now(businessZone) : instant.atZone(businessZone).toLocalDate();
    }

    private Instant toInstant(LocalDate date) {
        return date == null ? null : date.atStartOfDay(businessZone).toInstant();
    }

    private TenantContext adminContext() {
        TenantContext context = tenantContextService.requireForUser(currentUserService.requireUserId());
        tenantGuard.requireOwnerOrAdmin(context);
        return context;
    }
}

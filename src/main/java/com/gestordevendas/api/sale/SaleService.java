package com.gestordevendas.api.sale;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.client.ClientRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.pricing.ClientProductPriceRepository;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.report.BusinessTimeProperties;
import com.gestordevendas.api.stock.StockMovementType;
import com.gestordevendas.api.stock.StockService;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import com.gestordevendas.api.user.CompanyRole;
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
public class SaleService {
    private final SaleRepository repository;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final SaleStateRepository stateRepository;
    private final SaleNumberService numberService;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final ClientProductPriceRepository priceRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final AuditService auditService;
    private final ZoneId businessZone;

    public SaleService(SaleRepository repository, SaleItemRepository itemRepository, SalePaymentRepository paymentRepository,
                       SaleStateRepository stateRepository, SaleNumberService numberService,
                       ProductRepository productRepository, ClientRepository clientRepository,
                       ClientProductPriceRepository priceRepository, CompanyRepository companyRepository,
                       UserRepository userRepository, StockService stockService, CurrentUserService currentUserService,
                       TenantContextService tenantContextService, TenantGuard tenantGuard, AuditService auditService,
                       BusinessTimeProperties businessTimeProperties) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.paymentRepository = paymentRepository;
        this.stateRepository = stateRepository;
        this.numberService = numberService;
        this.productRepository = productRepository;
        this.clientRepository = clientRepository;
        this.priceRepository = priceRepository;
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
    public List<SaleResponse> list() {
        TenantContext context = currentTenant();
        return repository.findAllByCompanyIdOrderBySoldAtDesc(context.companyId()).stream()
            .map(s -> response(s, context)).toList();
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        TenantContext context = currentTenant();
        return response(require(id, context.companyId()), context);
    }

    @Transactional
    public SaleResponse create(SaleRequest request) {
        TenantContext context = currentTenant();
        validateUniqueProducts(request.items());
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        User user = userRepository.findById(context.userId()).orElseThrow();
        Client client = requireClient(request.clientId(), context.companyId());
        Sale sale = repository.save(Sale.create(company, numberService.next(context.companyId()), client,
            request.soldAt(), request.paymentType()));
        SaleState state = stateRepository.save(SaleState.create(sale, request.paymentType(), user));
        sale.applyRuntimeState(state.getStatus(), state.getPaymentType(), state.getCancelReason());

        List<SaleItem> items = buildItems(request.items(), context, company, sale, client);
        for (SaleItem item : items.stream().filter(SaleItem::isStockMoved)
            .sorted(Comparator.comparing(i -> i.getProduct().getId())).toList()) {
            stockService.applyDelta(context, item.getProduct(), item.getQuantity().negate(), StockMovementType.VENDA,
                "VENDA", sale.getId(), null, "INSUFFICIENT_STOCK", "Estoque insuficiente para este produto.");
        }
        itemRepository.saveAll(items);
        applyTotals(sale, items, request.paymentType());
        auditService.record("SALE_CREATED", context.companyId(), context.userId(), "SALE", sale.getId(), null,
            Map.of("number", sale.getNumber(), "total", sale.getTotal()));
        return response(sale, items, context);
    }

    @Transactional
    public SaleResponse update(UUID id, SaleRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        validateUniqueProducts(request.items());
        Sale sale = requireActive(id, context.companyId());
        BigDecimal paid = paid(context.companyId(), id);
        SalePaymentType currentType = resolvedPaymentType(sale);
        if (currentType == SalePaymentType.PRAZO && request.paymentType() == SalePaymentType.AVISTA && paid.signum() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAID_SALE_CANNOT_BECOME_CASH",
                "Venda com pagamentos registrados não pode ser alterada para à vista.");
        }
        Client client = requireClient(request.clientId(), context.companyId());
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        List<SaleItem> oldItems = itemRepository.findAllByCompanyIdAndSaleId(context.companyId(), id);
        List<SaleItem> newItems = buildItems(request.items(), context, company, sale, client);
        BigDecimal newTotal = newItems.stream().map(SaleItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.paymentType() == SalePaymentType.PRAZO && newTotal.compareTo(paid) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SALE_TOTAL_BELOW_PAID",
                "O total da venda não pode ser menor que o valor já recebido.");
        }
        applySaleStockDelta(context, oldItems, newItems, id);
        itemRepository.deleteAllByCompanyIdAndSaleId(context.companyId(), id);
        itemRepository.flush();
        itemRepository.saveAll(newItems);
        BigDecimal cost = newItems.stream().map(SaleItem::getLineCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        sale.update(client, request.soldAt(), request.paymentType(), money(newTotal), money(cost));

        SaleState state = stateRepository.findById(id).orElseGet(() -> SaleState.create(sale, currentType,
            userRepository.findById(context.userId()).orElseThrow()));
        state.setPaymentType(request.paymentType());
        stateRepository.save(state);
        sale.applyRuntimeState(state.getStatus(), request.paymentType(), state.getCancelReason());

        if (request.paymentType() == SalePaymentType.PRAZO && paid.compareTo(sale.getTotal()) >= 0) sale.markPaid(Instant.now());
        auditService.record("SALE_UPDATED", context.companyId(), context.userId(), "SALE", sale.getId(), null,
            Map.of("number", sale.getNumber(), "total", sale.getTotal()));
        return response(sale, newItems, context);
    }

    @Transactional
    public SaleResponse cancel(UUID id, SaleCancelRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Sale sale = requireActive(id, context.companyId());
        List<SaleItem> items = itemRepository.findAllByCompanyIdAndSaleId(context.companyId(), id);
        for (SaleItem item : items.stream().filter(SaleItem::isStockMoved)
            .sorted(Comparator.comparing(i -> i.getProduct().getId())).toList()) {
            stockService.applyDelta(context, item.getProduct(), item.getQuantity(), StockMovementType.VENDA_REVERSAO,
                "VENDA", id, request.reason(), "STOCK_REVERSAL_NOT_ALLOWED", "Não foi possível devolver o estoque da venda.");
        }
        User user = userRepository.findById(context.userId()).orElseThrow();
        SaleState state = stateRepository.findById(id)
            .orElseGet(() -> SaleState.create(sale, resolvedPaymentType(sale), user));
        state.cancel(user, request.reason(), Instant.now());
        stateRepository.save(state);
        sale.applyRuntimeState(SaleStatus.CANCELADA, state.getPaymentType(), state.getCancelReason());
        auditService.record("SALE_CANCELLED", context.companyId(), context.userId(), "SALE", sale.getId(), request.reason(),
            Map.of("number", sale.getNumber()));
        return response(sale, items, context);
    }

    @Transactional
    public SaleResponse addPayment(UUID id, PaymentRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Sale sale = requireActive(id, context.companyId());
        if (resolvedPaymentType(sale) != SalePaymentType.PRAZO) {
            throw new ApiException(HttpStatus.CONFLICT, "SALE_NOT_CREDIT", "Venda não está a prazo.");
        }
        BigDecimal outstanding = outstanding(sale, context.companyId());
        if (request.amount().compareTo(outstanding) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_BALANCE", "Pagamento maior que o saldo em aberto.");
        }
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        User user = userRepository.findById(context.userId()).orElseThrow();
        paymentRepository.save(SalePayment.create(company, sale, request.amount(), user, LocalDate.now(businessZone)));
        if (request.amount().compareTo(outstanding) == 0) sale.markPaid(Instant.now());
        auditService.record("SALE_PAYMENT_ADDED", context.companyId(), context.userId(), "SALE", sale.getId(), null,
            Map.of("amount", request.amount()));
        return response(sale, context);
    }

    @Transactional
    public SaleResponse settle(UUID id) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Sale sale = requireActive(id, context.companyId());
        if (resolvedPaymentType(sale) != SalePaymentType.PRAZO) {
            throw new ApiException(HttpStatus.CONFLICT, "SALE_NOT_CREDIT", "Venda não está a prazo.");
        }
        BigDecimal outstanding = outstanding(sale, context.companyId());
        if (outstanding.signum() > 0) {
            Company company = companyRepository.findById(context.companyId()).orElseThrow();
            User user = userRepository.findById(context.userId()).orElseThrow();
            paymentRepository.save(SalePayment.create(company, sale, outstanding, user, LocalDate.now(businessZone)));
        }
        sale.markPaid(Instant.now());
        auditService.record("SALE_SETTLED", context.companyId(), context.userId(), "SALE", sale.getId(), null, Map.of());
        return response(sale, context);
    }

    public Sale require(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SALE_NOT_FOUND", "Venda não encontrada."));
    }

    public Sale requireActive(UUID id, UUID companyId) {
        Sale sale = require(id, companyId);
        if (resolvedStatus(sale) == SaleStatus.CANCELADA) {
            throw new ApiException(HttpStatus.CONFLICT, "SALE_CANCELLED", "Venda cancelada.");
        }
        return sale;
    }

    public BigDecimal paid(UUID companyId, UUID saleId) {
        return money(paymentRepository.sumPaid(companyId, saleId));
    }

    public BigDecimal outstanding(Sale sale, UUID companyId) {
        if (resolvedStatus(sale) == SaleStatus.CANCELADA || resolvedPaymentType(sale) != SalePaymentType.PRAZO) {
            return money(BigDecimal.ZERO);
        }
        return money(sale.getTotal().subtract(paid(companyId, sale.getId())).max(BigDecimal.ZERO));
    }

    public SaleStatus resolvedStatus(Sale sale) {
        return stateRepository.findById(sale.getId()).map(SaleState::getStatus).orElse(sale.getStatus());
    }

    public SalePaymentType resolvedPaymentType(Sale sale) {
        return stateRepository.findById(sale.getId()).map(SaleState::getPaymentType)
            .filter(Objects::nonNull).orElse(sale.getPaymentType());
    }

    public String resolvedCancelReason(Sale sale) {
        return stateRepository.findById(sale.getId()).map(SaleState::getCancelReason).orElse(sale.getCancelReason());
    }

    private List<SaleItem> buildItems(List<SaleItemRequest> requests, TenantContext context, Company company, Sale sale, Client client) {
        return requests.stream().map(r -> {
            Product product = productRepository.findByIdAndCompanyIdAndActiveTrue(r.productId(), context.companyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
            BigDecimal price = r.unitPrice();
            if (price == null && client != null) {
                price = priceRepository.findByCompanyIdAndClientIdAndProductId(context.companyId(), client.getId(), product.getId())
                    .map(v -> v.getPrice()).orElse(null);
            }
            if (price == null) price = product.getSalePrice();
            BigDecimal lineTotal = money(price.multiply(r.quantity()));
            BigDecimal lineCost = money(product.getCostPrice().multiply(r.quantity()));
            return SaleItem.create(company, sale, product, r.quantity(), money(price), money(product.getCostPrice()), lineTotal, lineCost);
        }).toList();
    }

    private void applySaleStockDelta(TenantContext context, List<SaleItem> oldItems, List<SaleItem> newItems, UUID saleId) {
        Map<UUID, BigDecimal> oldQty = stockQuantities(oldItems);
        Map<UUID, BigDecimal> newQty = stockQuantities(newItems);
        Map<UUID, Product> products = new HashMap<>();
        oldItems.forEach(i -> products.put(i.getProduct().getId(), i.getProduct()));
        newItems.forEach(i -> products.put(i.getProduct().getId(), i.getProduct()));
        Set<UUID> ids = new HashSet<>(oldQty.keySet());
        ids.addAll(newQty.keySet());
        for (UUID productId : ids.stream().sorted().toList()) {
            BigDecimal stockDelta = oldQty.getOrDefault(productId, BigDecimal.ZERO)
                .subtract(newQty.getOrDefault(productId, BigDecimal.ZERO));
            if (stockDelta.signum() != 0) {
                stockService.applyDelta(context, products.get(productId), stockDelta,
                    stockDelta.signum() > 0 ? StockMovementType.VENDA_REVERSAO : StockMovementType.VENDA,
                    "VENDA", saleId, "Edição de venda", "INSUFFICIENT_STOCK", "Estoque insuficiente para editar a venda.");
            }
        }
    }

    private Map<UUID, BigDecimal> stockQuantities(List<SaleItem> items) {
        return items.stream().filter(SaleItem::isStockMoved)
            .collect(Collectors.toMap(i -> i.getProduct().getId(), SaleItem::getQuantity, BigDecimal::add));
    }

    private void applyTotals(Sale sale, List<SaleItem> items, SalePaymentType paymentType) {
        BigDecimal total = items.stream().map(SaleItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = items.stream().map(SaleItem::getLineCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        sale.update(sale.getClient(), sale.getSoldAt(), paymentType, money(total), money(cost));
    }

    private Client requireClient(UUID clientId, UUID companyId) {
        if (clientId == null) return null;
        return clientRepository.findByIdAndCompanyIdAndActiveTrue(clientId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Cliente não encontrado."));
    }

    private void validateUniqueProducts(List<SaleItemRequest> items) {
        if (items.stream().map(SaleItemRequest::productId).distinct().count() != items.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT", "Produto duplicado na venda.");
        }
    }

    private SaleResponse response(Sale sale, TenantContext context) {
        return response(sale, itemRepository.findAllByCompanyIdAndSaleId(context.companyId(), sale.getId()), context);
    }

    private SaleResponse response(Sale sale, List<SaleItem> items, TenantContext context) {
        boolean seeCost = context.role() == CompanyRole.OWNER || context.role() == CompanyRole.ADMIN;
        SaleStatus status = resolvedStatus(sale);
        SalePaymentType paymentType = resolvedPaymentType(sale);
        sale.applyRuntimeState(status, paymentType, resolvedCancelReason(sale));
        BigDecimal paid = paid(context.companyId(), sale.getId());
        BigDecimal outstanding = outstanding(sale, context.companyId());
        String clientName = sale.getClient() == null ? sale.getClientNameSnapshot() : sale.getClient().getName();
        return new SaleResponse(sale.getId(), sale.getNumber(), sale.getClient() == null ? null : sale.getClient().getId(),
            clientName, sale.getSoldAt(), paymentType, status, sale.getTotal(),
            seeCost ? sale.getCostTotal() : null, seeCost ? sale.getProfitTotal() : null, paid, outstanding,
            resolvedCancelReason(sale),
            items.stream().map(i -> new SaleItemResponse(i.getProduct().getId(), i.getProductName(), i.getQuantity(), i.getUnitPrice(),
                seeCost ? i.getUnitCost() : null, i.getLineTotal(), seeCost ? i.getLineCost() : null)).toList());
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

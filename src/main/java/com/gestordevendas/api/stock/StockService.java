package com.gestordevendas.api.stock;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class StockService {
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final AuditService auditService;

    public StockService(StockMovementRepository movementRepository, ProductRepository productRepository,
                        CompanyRepository companyRepository, UserRepository userRepository,
                        CurrentUserService currentUserService, TenantContextService tenantContextService,
                        TenantGuard tenantGuard, AuditService auditService) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard; this.auditService = auditService;
    }

    @Transactional
    public StockAdjustmentResponse adjust(StockAdjustmentRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        if (request.quantityDelta().signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZERO_STOCK_ADJUSTMENT", "O ajuste de estoque não pode ser zero.");
        }
        Product product = productRepository.findByIdAndCompanyIdAndActiveTrue(request.productId(), context.companyId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
        if (!product.isStockControlled()) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_NOT_CONTROLLED", "Este produto não controla estoque.");
        }
        StockMovementType type = request.quantityDelta().signum() > 0
            ? StockMovementType.AJUSTE_ENTRADA : StockMovementType.AJUSTE_SAIDA;
        applyDelta(context, product, request.quantityDelta(), type, "AJUSTE", null, request.reason(),
            "INSUFFICIENT_STOCK", "Estoque insuficiente para este ajuste.");
        auditService.record("STOCK_ADJUSTED", context.companyId(), context.userId(), "PRODUCT", product.getId(), request.reason(),
            java.util.Map.of("quantityDelta", request.quantityDelta(), "currentStock", product.getCurrentStock()));
        return new StockAdjustmentResponse(product.getId(), product.getCurrentStock());
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> list() {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        return movementRepository.findAllByCompanyIdOrderByCreatedAtDesc(context.companyId()).stream()
            .map(m -> new StockMovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getName(),
                m.getType(), m.getQuantityDelta(), m.getBalanceAfter(), m.getReferenceType(),
                m.getReferenceId(), m.getReason(), m.getCreatedAt()))
            .toList();
    }

    public void applyDelta(TenantContext context, Product product, BigDecimal delta, StockMovementType type,
                           String referenceType, UUID referenceId, String reason,
                           String negativeCode, String negativeMessage) {
        boolean historicalReversal = type == StockMovementType.COMPRA_REVERSAO || type == StockMovementType.VENDA_REVERSAO;
        if ((!product.isStockControlled() && !historicalReversal) || delta.signum() == 0) return;
        Product locked = productRepository.findForStockUpdate(product.getId(), context.companyId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
        try {
            locked.adjustStock(delta);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, negativeCode, negativeMessage);
        }
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        User user = userRepository.findById(context.userId()).orElseThrow();
        movementRepository.save(StockMovement.create(company, locked, type, delta, locked.getCurrentStock(),
            referenceType, referenceId, reason, user));
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

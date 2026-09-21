package com.gestordevendas.api.order;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.sale.Sale;
import com.gestordevendas.api.sale.SaleService;
import com.gestordevendas.api.sale.SaleStatus;
import com.gestordevendas.api.tenant.*;
import com.gestordevendas.api.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerOrderService {
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(10);

    private final CustomerOrderRepository repository;
    private final CustomerOrderItemRepository itemRepository;
    private final CustomerOrderLockRepository lockRepository;
    private final UserRepository userRepository;
    private final SaleService saleService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final AuditService auditService;

    public CustomerOrderService(CustomerOrderRepository repository, CustomerOrderItemRepository itemRepository,
                                CustomerOrderLockRepository lockRepository, UserRepository userRepository,
                                SaleService saleService, CurrentUserService currentUserService,
                                TenantContextService tenantContextService, TenantGuard tenantGuard, AuditService auditService) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.lockRepository = lockRepository;
        this.userRepository = userRepository;
        this.saleService = saleService;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderResponse> list() {
        TenantContext context = currentTenant();
        return repository.findAllByCompanyIdOrderByCreatedAtDesc(context.companyId()).stream()
            .map(order -> response(order, context.companyId())).toList();
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse get(UUID id) {
        TenantContext context = currentTenant();
        return response(require(id, context.companyId()), context.companyId());
    }

    @Transactional
    public CustomerOrderResponse viewed(UUID id) {
        TenantContext context = currentTenant();
        CustomerOrder order = require(id, context.companyId());
        if (order.getStatus() == CustomerOrderStatus.PENDENTE) order.markViewed(Instant.now());
        return response(order, context.companyId());
    }

    @Transactional
    public OrderClaimResponse claim(UUID id) {
        TenantContext context = currentTenant();
        CustomerOrder order = repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        if (order.getStatus() != CustomerOrderStatus.PENDENTE) return new OrderClaimResponse(false);
        User user = userRepository.findById(context.userId()).orElseThrow();
        Instant now = Instant.now();
        CustomerOrderLock lock = lockRepository.findForUpdate(id).orElse(null);
        if (lock == null) {
            lockRepository.save(CustomerOrderLock.create(id, user, now));
            return new OrderClaimResponse(true);
        }
        if (lock.getLockedBy().getId().equals(user.getId()) || lock.expired(now, CLAIM_TIMEOUT)) {
            lock.refresh(user, now);
            return new OrderClaimResponse(true);
        }
        return new OrderClaimResponse(false);
    }

    @Transactional
    public void release(UUID id) {
        TenantContext context = currentTenant();
        repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        CustomerOrderLock lock = lockRepository.findForUpdate(id).orElse(null);
        if (lock == null) return;
        boolean force = context.role() == CompanyRole.OWNER || context.role() == CompanyRole.ADMIN;
        if (force || lock.getLockedBy().getId().equals(context.userId())) lockRepository.delete(lock);
    }

    @Transactional
    public CustomerOrderResponse reject(UUID id) {
        TenantContext context = currentTenant();
        CustomerOrder order = repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        if (order.getStatus() != CustomerOrderStatus.PENDENTE) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PENDING", "Pedido não está pendente.");
        }
        order.reject();
        lockRepository.findById(id).ifPresent(lockRepository::delete);
        auditService.record("CUSTOMER_ORDER_REJECTED", context.companyId(), context.userId(), "CUSTOMER_ORDER", id, null, Map.of());
        return response(order, context.companyId());
    }

    @Transactional
    public CustomerOrderResponse converted(UUID id, OrderConversionRequest request) {
        TenantContext context = currentTenant();
        CustomerOrder order = repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        if (order.getStatus() != CustomerOrderStatus.PENDENTE) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PENDING", "Pedido não está pendente.");
        }
        Sale sale = saleService.requireActive(request.saleId(), context.companyId());
        if (saleService.resolvedStatus(sale) != SaleStatus.ATIVA || sale.getClient() == null
            || !sale.getClient().getId().equals(order.getClient().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_SALE_MISMATCH",
                "A venda precisa pertencer ao mesmo cliente do pedido.");
        }
        User converter = userRepository.findById(context.userId()).orElseThrow();
        order.convert(sale, converter, Instant.now());
        lockRepository.findById(id).ifPresent(lockRepository::delete);
        auditService.record("CUSTOMER_ORDER_CONVERTED", context.companyId(), context.userId(), "CUSTOMER_ORDER", id, null,
            Map.of("saleId", sale.getId().toString()));
        return response(order, context.companyId());
    }

    @Transactional
    public void deleteRejected(UUID id) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        CustomerOrder order = require(id, context.companyId());
        if (order.getStatus() != CustomerOrderStatus.RECUSADO) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_REJECTED", "Somente pedidos recusados podem ser excluídos.");
        }
        lockRepository.findById(id).ifPresent(lockRepository::delete);
        repository.delete(order);
        auditService.record("CUSTOMER_ORDER_DELETED", context.companyId(), context.userId(), "CUSTOMER_ORDER", id, null, Map.of());
    }

    private CustomerOrder require(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_ORDER_NOT_FOUND", "Pedido não encontrado.");
    }

    private CustomerOrderResponse response(CustomerOrder order, UUID companyId) {
        return PublicOrderService.response(order,
            itemRepository.findAllByCompanyIdAndOrderIdOrderByCreatedAtAsc(companyId, order.getId()));
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

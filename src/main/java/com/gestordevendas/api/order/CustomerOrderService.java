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

import java.time.*;
import java.util.*;

@Service
public class CustomerOrderService {
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(10);
    private final CustomerOrderRepository repository;
    private final CustomerOrderItemRepository itemRepository;
    private final UserRepository userRepository;
    private final SaleService saleService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final AuditService auditService;

    public CustomerOrderService(CustomerOrderRepository repository, CustomerOrderItemRepository itemRepository,
                                UserRepository userRepository, SaleService saleService, CurrentUserService currentUserService,
                                TenantContextService tenantContextService, TenantGuard tenantGuard, AuditService auditService) {
        this.repository = repository; this.itemRepository = itemRepository; this.userRepository = userRepository; this.saleService = saleService;
        this.currentUserService = currentUserService; this.tenantContextService = tenantContextService; this.tenantGuard = tenantGuard; this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderResponse> list() {
        TenantContext context = currentTenant();
        return repository.findAllByCompanyIdOrderByCreatedAtDesc(context.companyId()).stream().map(order -> response(order, context.companyId())).toList();
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse get(UUID id) { TenantContext context = currentTenant(); return response(require(id, context.companyId()), context.companyId()); }

    @Transactional
    public CustomerOrderResponse viewed(UUID id) {
        TenantContext context = currentTenant(); CustomerOrder order = require(id, context.companyId());
        if (order.getStatus() == CustomerOrderStatus.PENDENTE) order.markViewed(Instant.now());
        return response(order, context.companyId());
    }

    @Transactional
    public OrderClaimResponse claim(UUID id) {
        TenantContext context = currentTenant(); CustomerOrder order = repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        User user = userRepository.findById(context.userId()).orElseThrow();
        return new OrderClaimResponse(order.claim(user, Instant.now(), CLAIM_TIMEOUT));
    }

    @Transactional
    public void release(UUID id) {
        TenantContext context = currentTenant(); CustomerOrder order = repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        boolean force = context.role() == CompanyRole.OWNER || context.role() == CompanyRole.ADMIN;
        order.releaseClaim(context.userId(), force);
    }

    @Transactional
    public CustomerOrderResponse reject(UUID id) {
        TenantContext context = currentTenant(); CustomerOrder order = repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        if (order.getStatus() != CustomerOrderStatus.PENDENTE) throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PENDING", "Pedido não está pendente.");
        order.reject();
        auditService.record("CUSTOMER_ORDER_REJECTED", context.companyId(), context.userId(), "CUSTOMER_ORDER", id, null, Map.of());
        return response(order, context.companyId());
    }

    @Transactional
    public CustomerOrderResponse converted(UUID id, OrderConversionRequest request) {
        TenantContext context = currentTenant(); CustomerOrder order = repository.findForUpdate(id, context.companyId()).orElseThrow(this::notFound);
        if (order.getStatus() != CustomerOrderStatus.PENDENTE) throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PENDING", "Pedido não está pendente.");
        Sale sale = saleService.requireActive(request.saleId(), context.companyId());
        if (sale.getStatus() != SaleStatus.ATIVA || sale.getClient() == null || !sale.getClient().getId().equals(order.getClient().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_SALE_MISMATCH", "A venda precisa pertencer ao mesmo cliente do pedido.");
        }
        order.convert(sale);
        auditService.record("CUSTOMER_ORDER_CONVERTED", context.companyId(), context.userId(), "CUSTOMER_ORDER", id, null,
            Map.of("saleId", sale.getId().toString()));
        return response(order, context.companyId());
    }

    @Transactional
    public void deleteRejected(UUID id) {
        TenantContext context = currentTenant(); tenantGuard.requireOwnerOrAdmin(context);
        CustomerOrder order = require(id, context.companyId());
        if (order.getStatus() != CustomerOrderStatus.RECUSADO) throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_REJECTED", "Somente pedidos recusados podem ser excluídos.");
        repository.delete(order);
        auditService.record("CUSTOMER_ORDER_DELETED", context.companyId(), context.userId(), "CUSTOMER_ORDER", id, null, Map.of());
    }

    private CustomerOrder require(UUID id, UUID companyId) { return repository.findByIdAndCompanyId(id, companyId).orElseThrow(this::notFound); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_ORDER_NOT_FOUND", "Pedido não encontrado."); }
    private CustomerOrderResponse response(CustomerOrder order, UUID companyId) {
        return PublicOrderService.response(order, itemRepository.findAllByCompanyIdAndOrderIdOrderByCreatedAtAsc(companyId, order.getId()));
    }
    private TenantContext currentTenant() { return tenantContextService.requireForUser(currentUserService.requireUserId()); }
}

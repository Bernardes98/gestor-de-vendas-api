package com.gestordevendas.api.order;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.client.ClientRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.common.token.SecureTokenService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ClientOrderSettingsService {
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final ClientHiddenProductRepository hiddenRepository;
    private final SecureTokenService tokenService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final AuditService auditService;

    public ClientOrderSettingsService(ClientRepository clientRepository, ProductRepository productRepository,
                                      ClientHiddenProductRepository hiddenRepository, SecureTokenService tokenService,
                                      CurrentUserService currentUserService, TenantContextService tenantContextService,
                                      TenantGuard tenantGuard, AuditService auditService) {
        this.clientRepository = clientRepository; this.productRepository = productRepository; this.hiddenRepository = hiddenRepository;
        this.tokenService = tokenService; this.currentUserService = currentUserService; this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard; this.auditService = auditService;
    }

    @Transactional
    public OrderLinkResponse regenerate(UUID clientId) {
        TenantContext context = currentTenant(); tenantGuard.requireOwnerOrAdmin(context);
        Client client = requireClient(clientId, context.companyId());
        client.rotateOrderToken(tokenService.generateRawToken());
        auditService.record("CLIENT_ORDER_LINK_REGENERATED", context.companyId(), context.userId(), "CLIENT", clientId, null, Map.of());
        return new OrderLinkResponse(client.getOrderToken());
    }

    @Transactional(readOnly = true)
    public List<UUID> hiddenProducts(UUID clientId) {
        TenantContext context = currentTenant(); tenantGuard.requireOwnerOrAdmin(context);
        requireClient(clientId, context.companyId());
        return hiddenRepository.findAllByCompanyIdAndClientId(context.companyId(), clientId).stream()
            .map(value -> value.getProduct().getId()).sorted().toList();
    }

    @Transactional
    public void saveHiddenProducts(UUID clientId, HiddenProductsRequest request) {
        TenantContext context = currentTenant(); tenantGuard.requireOwnerOrAdmin(context);
        Client client = requireClient(clientId, context.companyId());
        Company company = client.getCompany();
        List<UUID> ids = request.productIds().stream().distinct().toList();
        List<Product> products = new ArrayList<>();
        for (UUID id : ids) {
            products.add(productRepository.findByIdAndCompanyId(id, context.companyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado.")));
        }
        hiddenRepository.deleteAllByCompanyIdAndClientId(context.companyId(), clientId);
        hiddenRepository.flush();
        hiddenRepository.saveAll(products.stream().map(product -> ClientHiddenProduct.create(company, client, product)).toList());
        auditService.record("CLIENT_ORDER_HIDDEN_PRODUCTS_UPDATED", context.companyId(), context.userId(), "CLIENT", clientId, null,
            Map.of("hiddenCount", ids.size()));
    }

    private Client requireClient(UUID id, UUID companyId) {
        return clientRepository.findByIdAndCompanyIdAndActiveTrue(id, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Cliente não encontrado."));
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

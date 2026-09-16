package com.gestordevendas.api.pricing;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.client.ClientService;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductService;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ClientProductPriceService {
    private final ClientProductPriceRepository repository;
    private final ClientService clientService;
    private final ProductService productService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public ClientProductPriceService(ClientProductPriceRepository repository, ClientService clientService,
                                     ProductService productService, CurrentUserService currentUserService,
                                     TenantContextService tenantContextService, TenantGuard tenantGuard) {
        this.repository = repository;
        this.clientService = clientService;
        this.productService = productService;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public ProductPriceResponse get(UUID clientId, UUID productId) {
        TenantContext context = currentTenant();
        clientService.requireActive(clientId, context.companyId());
        Product product = productService.requireActive(productId, context.companyId());
        return repository.findByCompanyIdAndClientIdAndProductId(context.companyId(), clientId, productId)
            .map(value -> new ProductPriceResponse(clientId, productId, value.getPrice(), true))
            .orElseGet(() -> new ProductPriceResponse(clientId, productId, product.getSalePrice(), false));
    }

    @Transactional
    public ProductPriceResponse put(UUID clientId, UUID productId, ProductPriceRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Client client = clientService.requireActive(clientId, context.companyId());
        Product product = productService.requireActive(productId, context.companyId());
        ClientProductPrice value = repository.findByCompanyIdAndClientIdAndProductId(context.companyId(), clientId, productId)
            .orElseGet(() -> ClientProductPrice.create(client.getCompany(), client, product, request.price()));
        value.setPrice(request.price());
        repository.save(value);
        return new ProductPriceResponse(clientId, productId, value.getPrice(), true);
    }

    @Transactional
    public void delete(UUID clientId, UUID productId) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        clientService.requireActive(clientId, context.companyId());
        productService.requireActive(productId, context.companyId());
        repository.deleteByCompanyIdAndClientIdAndProductId(context.companyId(), clientId, productId);
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

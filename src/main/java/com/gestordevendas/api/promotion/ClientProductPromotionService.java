package com.gestordevendas.api.promotion;
import com.gestordevendas.api.client.*;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.product.*;
import com.gestordevendas.api.tenant.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ClientProductPromotionService {
    private final ClientProductPromotionRepository repository; private final ClientService clientService; private final ProductService productService;
    private final CurrentUserService currentUserService; private final TenantContextService tenantContextService; private final TenantGuard tenantGuard;
    public ClientProductPromotionService(ClientProductPromotionRepository repository, ClientService clientService, ProductService productService,
      CurrentUserService currentUserService, TenantContextService tenantContextService, TenantGuard tenantGuard) {
      this.repository=repository; this.clientService=clientService; this.productService=productService; this.currentUserService=currentUserService; this.tenantContextService=tenantContextService; this.tenantGuard=tenantGuard;
    }
    private TenantContext tenant(){ return tenantContextService.requireForUser(currentUserService.requireUserId()); }
    @Transactional(readOnly=true) public List<PromotionResponse> list(UUID clientId){ TenantContext c=tenant(); clientService.requireActive(clientId,c.companyId()); return repository.findAllByCompany_IdAndClient_Id(c.companyId(),clientId).stream().map(v->new PromotionResponse(v.getProductId(),v.getPromotionalPrice(),v.getMinimumQuantity())).toList(); }
    @Transactional public PromotionResponse put(UUID clientId, UUID productId, PromotionRequest req){ TenantContext c=tenant(); tenantGuard.requireOwnerOrAdmin(c); Client client=clientService.requireActive(clientId,c.companyId()); Product product=productService.requireActive(productId,c.companyId()); var v=repository.findByCompany_IdAndClient_IdAndProduct_Id(c.companyId(),clientId,productId).orElseGet(()->ClientProductPromotion.create(client.getCompany(),client,product,req.promotionalPrice(),req.minimumQuantity())); v.update(req.promotionalPrice(),req.minimumQuantity()); repository.save(v); return new PromotionResponse(productId,v.getPromotionalPrice(),v.getMinimumQuantity()); }
    @Transactional public void delete(UUID clientId, UUID productId){ TenantContext c=tenant(); tenantGuard.requireOwnerOrAdmin(c); clientService.requireActive(clientId,c.companyId()); repository.deleteByCompany_IdAndClient_IdAndProduct_Id(c.companyId(),clientId,productId); }
}

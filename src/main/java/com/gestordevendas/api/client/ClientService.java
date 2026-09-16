package com.gestordevendas.api.client;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {
    private final ClientRepository repository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public ClientService(ClientRepository repository, CompanyRepository companyRepository,
                         CurrentUserService currentUserService, TenantContextService tenantContextService,
                         TenantGuard tenantGuard) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        TenantContext context = currentTenant();
        return repository.findAllByCompanyIdAndActiveTrueOrderByNameAsc(context.companyId()).stream()
            .map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        return ClientResponse.from(requireActive(id, currentTenant().companyId()));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        Client client = Client.create(company, request.name());
        apply(client, request);
        return ClientResponse.from(repository.save(client));
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Client client = requireActive(id, context.companyId());
        apply(client, request);
        return ClientResponse.from(client);
    }

    @Transactional
    public void deactivate(UUID id) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        requireActive(id, context.companyId()).setActive(false);
    }

    public Client requireActive(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyIdAndActiveTrue(id, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Cliente não encontrado."));
    }

    private void apply(Client client, ClientRequest request) {
        client.update(request.name(), request.document(), request.phone(), request.email(), request.address(), request.city(), request.notes());
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

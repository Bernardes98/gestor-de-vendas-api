package com.gestordevendas.api.company;

import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanySettingsService {
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public CompanySettingsService(CompanyRepository companyRepository, CurrentUserService currentUserService,
                                  TenantContextService tenantContextService, TenantGuard tenantGuard) {
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public CompanySettingsResponse get() {
        TenantContext context = currentTenant();
        return CompanySettingsResponse.from(companyRepository.findById(context.companyId()).orElseThrow());
    }

    @Transactional
    public CompanySettingsResponse update(CompanySettingsRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        company.updateSettings(
            request.name(), request.legalName(), request.document(), request.phone(), request.email(),
            request.address(), request.city(), request.primaryColor(), request.secondaryColor()
        );
        return CompanySettingsResponse.from(company);
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

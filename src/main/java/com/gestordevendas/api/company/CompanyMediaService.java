package com.gestordevendas.api.company;

import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.storage.MediaStorageService;
import com.gestordevendas.api.storage.StoredObject;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CompanyMediaService {
    private final CompanyRepository companyRepository;
    private final MediaStorageService mediaStorageService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public CompanyMediaService(CompanyRepository companyRepository, MediaStorageService mediaStorageService,
                               CurrentUserService currentUserService, TenantContextService tenantContextService,
                               TenantGuard tenantGuard) {
        this.companyRepository = companyRepository;
        this.mediaStorageService = mediaStorageService;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
    }


    @Transactional(readOnly = true)
    public CompanyMediaResponse getLogo() {
        TenantContext context = currentTenant();
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        String key = company.getLogoKey();
        return new CompanyMediaResponse(key == null || key.isBlank() ? null : mediaStorageService.publicUrl(key));
    }

    @Transactional
    public CompanyMediaResponse uploadLogo(MultipartFile file) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        StoredObject stored = mediaStorageService.storeCompanyLogo(context.companyId(), file);
        String previous = company.getLogoKey();
        company.setLogoKey(stored.key());
        if (previous != null && !previous.isBlank()) {
            mediaStorageService.delete(previous);
        }
        return new CompanyMediaResponse(stored.url());
    }

    @Transactional
    public void deleteLogo() {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        if (company.getLogoKey() != null && !company.getLogoKey().isBlank()) {
            mediaStorageService.delete(company.getLogoKey());
            company.setLogoKey(null);
        }
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

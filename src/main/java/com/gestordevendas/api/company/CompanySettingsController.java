package com.gestordevendas.api.company;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company/settings")
public class CompanySettingsController {
    private final CompanySettingsService service;
    private final com.gestordevendas.api.tenant.TenantContextService tenantContext;
    private final com.gestordevendas.api.common.security.CurrentUserService currentUser;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public CompanySettingsController(CompanySettingsService service,
        com.gestordevendas.api.tenant.TenantContextService tenantContext,
        com.gestordevendas.api.common.security.CurrentUserService currentUser,
        org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.service = service;
        this.tenantContext = tenantContext;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
    }

    @GetMapping("/mobile-navigation")
    public java.util.List<String> mobileNavigation() {
        var companyId = tenantContext.requireForUser(currentUser.requireUserId()).companyId();
        var paths = jdbc.query("SELECT path FROM company_mobile_navigation WHERE company_id = ? ORDER BY position",
            (rs, i) -> rs.getString(1), companyId);
        return paths.isEmpty() ? java.util.List.of("/clientes", "/produtos", "/vender", "/vendas", "/a-receber") : paths;
    }

    @GetMapping
    public CompanySettingsResponse get() {
        return service.get();
    }

    @PutMapping
    public CompanySettingsResponse update(@Valid @RequestBody CompanySettingsRequest request) {
        return service.update(request);
    }
}

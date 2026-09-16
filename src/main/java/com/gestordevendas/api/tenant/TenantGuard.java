package com.gestordevendas.api.tenant;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TenantGuard {
    public void requireOwner(TenantContext context) {
        if (context.role() != CompanyRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED", "Apenas o proprietário pode realizar esta ação.");
        }
    }

    public void requireOwnerOrAdmin(TenantContext context) {
        if (context.role() != CompanyRole.OWNER && context.role() != CompanyRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Acesso administrativo necessário.");
        }
    }

    public void requireSameCompany(TenantContext context, CompanyMembership target) {
        if (!context.companyId().equals(target.getCompany().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado.");
        }
    }
}

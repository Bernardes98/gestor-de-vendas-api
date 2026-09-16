package com.gestordevendas.api.audit;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import com.gestordevendas.api.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditQueryService {
    private final AuditRepository repository;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final CompanyRepository companyRepository;

    public AuditQueryService(AuditRepository repository, CurrentUserService currentUserService,
                             TenantContextService tenantContextService, TenantGuard tenantGuard,
                             CompanyRepository companyRepository) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> currentCompany(String action, String entityType, Instant from, Instant to, int limit) {
        TenantContext context = tenantContextService.requireForUser(currentUserService.requireUserId());
        tenantGuard.requireOwner(context);
        return search(context.companyId(), action, entityType, from, to, limit);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> platformCompany(UUID companyId, String action, String entityType, Instant from, Instant to, int limit) {
        User user = currentUserService.requireCurrentUser();
        if (!user.isPlatformAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PLATFORM_ADMIN_REQUIRED", "Acesso de administrador da plataforma necessário.");
        }
        if (!companyRepository.existsById(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada.");
        }
        return search(companyId, action, entityType, from, to, limit);
    }

    private List<AuditResponse> search(UUID companyId, String action, String entityType, Instant from, Instant to, int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        String normalizedAction = blank(action);
        String normalizedType = blank(entityType);

        Specification<AuditEvent> specification = (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("company").get("id"), companyId);

        if (normalizedAction != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("action"), normalizedAction));
        }
        if (normalizedType != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("entityType"), normalizedType));
        }
        if (from != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));
        }

        PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(specification, page).getContent().stream()
            .map(AuditResponse::from)
            .toList();
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

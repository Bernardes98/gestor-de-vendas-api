package com.gestordevendas.api.category;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ProductCategoryService {
    private final ProductCategoryRepository repository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public ProductCategoryService(ProductCategoryRepository repository, ProductRepository productRepository,
                                  CompanyRepository companyRepository, CurrentUserService currentUserService,
                                  TenantContextService tenantContextService, TenantGuard tenantGuard) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        UUID companyId = currentTenant().companyId();
        return repository.findAllByCompanyIdOrderByOrderIndexAscNameAsc(companyId).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        ensureNameAvailable(context.companyId(), request.name(), null);
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        ProductCategory category = ProductCategory.create(company, request.name(), repository.maxOrder(context.companyId()) + 1);
        return CategoryResponse.from(repository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        ProductCategory category = require(id, context.companyId());
        ensureNameAvailable(context.companyId(), request.name(), category);
        category.setName(request.name());
        return CategoryResponse.from(category);
    }

    @Transactional
    public List<CategoryResponse> reorder(ReorderCategoriesRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        List<ProductCategory> categories = repository.findAllByCompanyIdOrderByOrderIndexAscNameAsc(context.companyId());
        if (request.categoryIds().size() != categories.size() || new HashSet<>(request.categoryIds()).size() != categories.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_ORDER", "A ordem deve conter todas as categorias uma única vez.");
        }
        var byId = categories.stream().collect(java.util.stream.Collectors.toMap(ProductCategory::getId, category -> category));
        for (int i = 0; i < request.categoryIds().size(); i++) {
            ProductCategory category = byId.get(request.categoryIds().get(i));
            if (category == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_ORDER", "Categoria inválida para esta empresa.");
            }
            category.setOrderIndex(i);
        }
        return request.categoryIds().stream().map(byId::get).map(CategoryResponse::from).toList();
    }

    @Transactional
    public void delete(UUID id) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        ProductCategory category = require(id, context.companyId());
        if (productRepository.countByCompanyIdAndCategoryId(context.companyId(), id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "A categoria possui produtos vinculados.");
        }
        repository.delete(category);
    }

    public ProductCategory require(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Categoria não encontrada."));
    }

    private void ensureNameAvailable(UUID companyId, String name, ProductCategory current) {
        boolean exists = repository.existsByCompanyIdAndNameIgnoreCase(companyId, name.trim());
        if (exists && (current == null || !current.getName().equalsIgnoreCase(name.trim()))) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_ALREADY_EXISTS", "Já existe uma categoria com este nome.");
        }
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

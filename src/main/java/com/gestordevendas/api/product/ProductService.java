package com.gestordevendas.api.product;

import com.gestordevendas.api.category.ProductCategory;
import com.gestordevendas.api.category.ProductCategoryRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.storage.ObjectStorage;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import com.gestordevendas.api.user.CompanyRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final ProductPhotoRepository photoRepository;
    private final ProductCategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;
    private final ObjectStorage objectStorage;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public ProductService(ProductRepository repository, ProductPhotoRepository photoRepository,
                          ProductCategoryRepository categoryRepository, CompanyRepository companyRepository,
                          ObjectStorage objectStorage, CurrentUserService currentUserService,
                          TenantContextService tenantContextService, TenantGuard tenantGuard) {
        this.repository = repository;
        this.photoRepository = photoRepository;
        this.categoryRepository = categoryRepository;
        this.companyRepository = companyRepository;
        this.objectStorage = objectStorage;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        TenantContext context = currentTenant();
        return repository.findAllByCompanyIdAndActiveTrueOrderByNameAsc(context.companyId()).stream()
            .map(product -> response(product, context)).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        TenantContext context = currentTenant();
        return response(requireActive(id, context.companyId()), context);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Company company = companyRepository.findById(context.companyId()).orElseThrow();
        Product product = Product.create(company, request.name(), request.salePrice());
        apply(product, request, context.companyId());
        repository.save(product);
        return response(product, context);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Product product = requireActive(id, context.companyId());
        apply(product, request, context.companyId());
        return response(product, context);
    }

    @Transactional
    public void deactivate(UUID id) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        requireActive(id, context.companyId()).setActive(false);
    }

    public Product requireActive(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyIdAndActiveTrue(id, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
    }

    private void apply(Product product, ProductRequest request, UUID companyId) {
        ProductCategory category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndCompanyId(request.categoryId(), companyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Categoria não encontrada."));
        }
        product.update(request.name(), request.code(), request.brand(), request.description(), category,
            request.costPrice(), request.salePrice(), request.stockControlled(),
            request.minimumStock() == null ? BigDecimal.ZERO : request.minimumStock());
    }

    private ProductResponse response(Product product, TenantContext context) {
        boolean canSeeCost = context.role() == CompanyRole.OWNER || context.role() == CompanyRole.ADMIN;
        BigDecimal margin = canSeeCost ? margin(product.getCostPrice(), product.getSalePrice()) : null;
        List<ProductPhotoResponse> photos = photoRepository
            .findAllByCompanyIdAndProductIdOrderByOrderIndexAsc(context.companyId(), product.getId()).stream()
            .map(photo -> new ProductPhotoResponse(photo.getId(), photo.getUrl(),
                photo.getContentType(), photo.getSizeBytes(), photo.getOrderIndex()))
            .toList();
        return new ProductResponse(product.getId(), product.getName(), product.getCode(), product.getBrand(), product.getDescription(),
            product.getCategory() == null ? null : product.getCategory().getId(),
            product.getCategory() == null ? null : product.getCategory().getName(), product.getSalePrice(),
            product.isStockControlled(), product.getCurrentStock(), product.getMinimumStock(), canSeeCost ? product.getCostPrice() : null, margin, photos);
    }

    private BigDecimal margin(BigDecimal cost, BigDecimal sale) {
        if (sale == null || sale.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return sale.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(sale, 2, RoundingMode.HALF_UP);
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

package com.gestordevendas.api.product;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.storage.MediaStorageService;
import com.gestordevendas.api.storage.StoredObject;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class ProductPhotoService {
    private final ProductService productService;
    private final ProductPhotoRepository repository;
    private final MediaStorageService mediaStorageService;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public ProductPhotoService(ProductService productService, ProductPhotoRepository repository,
                               MediaStorageService mediaStorageService, CurrentUserService currentUserService,
                               TenantContextService tenantContextService, TenantGuard tenantGuard) {
        this.productService = productService;
        this.repository = repository;
        this.mediaStorageService = mediaStorageService;
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional
    public ProductPhotoResponse upload(UUID productId, MultipartFile file) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        Product product = productService.requireActive(productId, context.companyId());
        StoredObject stored = mediaStorageService.storeProductImage(context.companyId(), productId, file);
        try {
            ProductPhoto photo = ProductPhoto.create(product.getCompany(), product, stored.key(), stored.contentType(),
                stored.sizeBytes(), repository.maxOrder(context.companyId(), productId) + 1);
            repository.save(photo);
            return new ProductPhotoResponse(photo.getId(), stored.url(), photo.getContentType(), photo.getSizeBytes(), photo.getOrderIndex());
        } catch (RuntimeException exception) {
            mediaStorageService.delete(stored.key());
            throw exception;
        }
    }

    @Transactional
    public void delete(UUID productId, UUID photoId) {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        productService.requireActive(productId, context.companyId());
        ProductPhoto photo = repository.findByIdAndCompanyIdAndProductId(photoId, context.companyId(), productId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Foto não encontrada."));
        mediaStorageService.delete(photo.getObjectKey());
        repository.delete(photo);
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }
}

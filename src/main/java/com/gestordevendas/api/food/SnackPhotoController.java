package com.gestordevendas.api.food;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.storage.MediaStorageService;
import com.gestordevendas.api.storage.StoredObject;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/food/snacks")
public class SnackPhotoController {
    private final SnackRepository repository;
    private final MediaStorageService storage;
    private final CurrentUserService users;
    private final TenantContextService tenants;
    private final TenantGuard guard;
    public SnackPhotoController(SnackRepository repository, MediaStorageService storage, CurrentUserService users, TenantContextService tenants, TenantGuard guard) {
        this.repository=repository; this.storage=storage; this.users=users; this.tenants=tenants; this.guard=guard;
    }
    @PostMapping(path="/{id}/photo", consumes="multipart/form-data")
    @Transactional
    public Map<String,String> upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        TenantContext tenant=tenants.requireForUser(users.requireUserId());
        guard.requireOwnerOrAdmin(tenant);
        Snack snack=repository.findByIdAndCompany_Id(id,tenant.companyId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SNACK_NOT_FOUND","Lanche não encontrado."));
        StoredObject uploaded=storage.storeSnackImage(tenant.companyId(),id,file);
        String previous=snack.getPhotoStorageKey();
        try {snack.setPhoto(uploaded.url(),uploaded.key()); repository.saveAndFlush(snack);}
        catch(RuntimeException ex){storage.delete(uploaded.key()); throw ex;}
        if(previous!=null&&!previous.isBlank()) storage.delete(previous);
        return Map.of("photoUrl",uploaded.url());
    }
    @DeleteMapping("/{id}/photo")
    @Transactional
    public void delete(@PathVariable UUID id) {
        TenantContext tenant=tenants.requireForUser(users.requireUserId());
        guard.requireOwnerOrAdmin(tenant);
        Snack snack=repository.findByIdAndCompany_Id(id,tenant.companyId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SNACK_NOT_FOUND","Lanche não encontrado."));
        String previous=snack.getPhotoStorageKey(); snack.setPhoto(null,null); repository.saveAndFlush(snack);
        if(previous!=null&&!previous.isBlank()) storage.delete(previous);
    }
}

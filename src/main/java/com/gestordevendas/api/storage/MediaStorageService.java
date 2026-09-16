package com.gestordevendas.api.storage;

import com.gestordevendas.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaStorageService {
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp"
    );

    private final ObjectStorage storage;

    public MediaStorageService(ObjectStorage storage) {
        this.storage = storage;
    }

    public StoredObject storeProductImage(UUID companyId, UUID productId, MultipartFile file) {
        return store("empresas/" + companyId + "/produtos/" + productId + "/", file);
    }

    public StoredObject storeCompanyLogo(UUID companyId, MultipartFile file) {
        return store("empresas/" + companyId + "/logo/", file);
    }

    public void delete(String key) {
        storage.delete(key);
    }

    public String publicUrl(String key) {
        return storage.publicUrl(key);
    }

    private StoredObject store(String prefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_IMAGE", "Selecione uma imagem.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "A imagem deve ter no máximo 5 MB.");
        }
        String extension = EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_TYPE", "A imagem deve ser JPEG, PNG ou WebP.");
        }
        String key = prefix + UUID.randomUUID() + "." + extension;
        try {
            byte[] bytes = file.getBytes();
            String url = storage.put(key, file.getContentType(), bytes);
            return new StoredObject(key, url, file.getContentType(), bytes.length);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_READ_FAILED", "Não foi possível ler a imagem.");
        }
    }
}

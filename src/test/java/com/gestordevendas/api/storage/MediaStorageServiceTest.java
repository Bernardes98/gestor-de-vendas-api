package com.gestordevendas.api.storage;

import com.gestordevendas.api.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageServiceTest {

    @Test
    void productImageKeyIsTenantScopedAndExtensionComesFromValidatedContentType() {
        RecordingStorage storage = new RecordingStorage();
        MediaStorageService service = new MediaStorageService(storage);
        UUID companyId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "anything.exe", "image/png", "content".getBytes(StandardCharsets.UTF_8));

        StoredObject result = service.storeProductImage(companyId, productId, file);

        assertThat(result.key()).startsWith("empresas/" + companyId + "/produtos/" + productId + "/");
        assertThat(result.key()).endsWith(".png");
        assertThat(storage.lastKey).isEqualTo(result.key());
    }

    @Test
    void rejectsUnsupportedImageType() {
        MediaStorageService service = new MediaStorageService(new RecordingStorage());
        MockMultipartFile file = new MockMultipartFile("file", "x.svg", "image/svg+xml", "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.storeCompanyLogo(UUID.randomUUID(), file))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("JPEG, PNG ou WebP");
    }

    private static final class RecordingStorage implements ObjectStorage {
        private String lastKey;

        @Override
        public String put(String key, String contentType, byte[] content) {
            this.lastKey = key;
            return "https://cdn.example.com/" + key;
        }

        @Override
        public void delete(String key) {
        }

        @Override
        public String publicUrl(String key) {
            return "https://cdn.example.com/" + key;
        }
    }
}

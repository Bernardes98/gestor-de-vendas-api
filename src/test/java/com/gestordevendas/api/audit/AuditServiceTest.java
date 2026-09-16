package com.gestordevendas.api.audit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuditServiceTest {

    @Test
    void rejectsSensitiveMetadataKeys() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditService auditService = new AuditService(repository, mock(EntityManager.class));
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> auditService.record(
            "TEST", companyId, userId, "USER", userId, null, Map.of("refreshToken", "raw")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sensível");
    }

    @Test
    void rejectsSensitiveNestedMetadataKeys() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditService auditService = new AuditService(repository, mock(EntityManager.class));

        assertThatThrownBy(() -> auditService.record(
            "TEST", null, null, null, null, null, Map.of("request", Map.of("authorization", "Bearer raw"))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

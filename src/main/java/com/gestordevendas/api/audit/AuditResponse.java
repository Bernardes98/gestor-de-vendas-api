package com.gestordevendas.api.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditResponse(UUID id, UUID userId, String userEmail, String action, String entityType,
                            UUID entityId, String reason, Map<String, Object> metadata, Instant createdAt) {
    static AuditResponse from(AuditEvent event) {
        return new AuditResponse(event.getId(), event.getUser() == null ? null : event.getUser().getId(),
            event.getUser() == null ? null : event.getUser().getEmail(), event.getAction(), event.getEntityType(),
            event.getEntityId(), event.getReason(), event.getMetadata(), event.getCreatedAt());
    }
}

package com.gestordevendas.api.audit;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditService {
    private static final Set<String> SENSITIVE_PARTS = Set.of("password", "token", "authorization", "secret");

    private final AuditRepository repository;
    private final EntityManager entityManager;

    public AuditService(AuditRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }


    @Transactional
    public void record(String action, UUID companyId, UUID userId, String entityType,
                       UUID entityId, String reason, Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        validateMetadata(safeMetadata);
        Company company = companyId == null ? null : entityManager.getReference(Company.class, companyId);
        User user = userId == null ? null : entityManager.getReference(User.class, userId);
        repository.save(new AuditEvent(UUID.randomUUID(), company, user, action, entityType, entityId, reason, safeMetadata));
    }

    private void validateMetadata(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nestedValue) -> {
                String normalized = String.valueOf(key).toLowerCase(Locale.ROOT);
                if (SENSITIVE_PARTS.stream().anyMatch(normalized::contains)) {
                    throw new IllegalArgumentException("Metadata de auditoria contém chave sensível: " + key);
                }
                validateMetadata(nestedValue);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(this::validateMetadata);
        }
    }
}

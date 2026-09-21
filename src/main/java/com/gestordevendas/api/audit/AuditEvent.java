package com.gestordevendas.api.audit;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "auditoria", schema = "api_internal")
public class AuditEvent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User user;

    @Column(name = "acao", nullable = false, length = 80)
    private String action;

    @Column(name = "entidade_tipo", length = 80)
    private String entityType;

    @Column(name = "entidade_id")
    private UUID entityId;

    @Column(name = "motivo", length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(UUID id, Company company, User user, String action, String entityType,
                      UUID entityId, String reason, Map<String, Object> metadata) {
        this.id = id;
        this.company = company;
        this.user = user;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reason = reason;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public User getUser() { return user; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Map<String, Object> getMetadata() { return metadata; }
}

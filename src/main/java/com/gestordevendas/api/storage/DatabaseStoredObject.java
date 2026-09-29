package com.gestordevendas.api.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_media")
public class DatabaseStoredObject {
    @Id
    private UUID id;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected DatabaseStoredObject() {}

    public static DatabaseStoredObject create(String objectKey, String contentType, byte[] content) {
        DatabaseStoredObject object = new DatabaseStoredObject();
        object.id = UUID.randomUUID();
        object.objectKey = objectKey;
        object.contentType = contentType;
        object.content = content;
        return object;
    }

    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}

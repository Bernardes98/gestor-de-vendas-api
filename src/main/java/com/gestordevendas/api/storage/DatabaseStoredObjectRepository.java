package com.gestordevendas.api.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DatabaseStoredObjectRepository extends JpaRepository<DatabaseStoredObject, UUID> {
    Optional<DatabaseStoredObject> findByObjectKey(String objectKey);
    void deleteByObjectKey(String objectKey);
}

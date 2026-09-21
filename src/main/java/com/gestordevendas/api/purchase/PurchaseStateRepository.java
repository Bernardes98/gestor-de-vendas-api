package com.gestordevendas.api.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseStateRepository extends JpaRepository<PurchaseState, UUID> {
}

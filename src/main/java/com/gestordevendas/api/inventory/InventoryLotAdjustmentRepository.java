package com.gestordevendas.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryLotAdjustmentRepository extends JpaRepository<InventoryLotAdjustment, UUID> {
}

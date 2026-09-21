package com.gestordevendas.api.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SaleStateRepository extends JpaRepository<SaleState, UUID> {
}

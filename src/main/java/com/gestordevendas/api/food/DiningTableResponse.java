package com.gestordevendas.api.food;

import java.util.UUID;

public record DiningTableResponse(UUID id, int number, String name) {
    static DiningTableResponse from(DiningTable table) {
        return new DiningTableResponse(table.getId(), table.getNumber(), table.getName());
    }
}

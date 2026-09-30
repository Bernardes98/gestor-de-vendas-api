package com.gestordevendas.api.route;
import jakarta.validation.constraints.NotNull;
import java.util.List; import java.util.UUID;
public record RouteReorderRequest(@NotNull List<UUID> clientIds) {}

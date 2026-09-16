package com.gestordevendas.api.category;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderCategoriesRequest(@NotEmpty List<UUID> categoryIds) {}

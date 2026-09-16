package com.gestordevendas.api.common.security;

import java.util.UUID;

public record CurrentUser(UUID id, String email, boolean platformAdmin) {
}

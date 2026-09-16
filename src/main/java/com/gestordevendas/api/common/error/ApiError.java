package com.gestordevendas.api.common.error;

public record ApiError(String code, String message, String requestId) {
}

package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Esquema estandarizado de respuesta de error para la API REST.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String errorCode, String message, OffsetDateTime timestamp, String path, Map<String, Object> details) {

    public ApiError(String errorCode, String message, OffsetDateTime timestamp, String path) {
        this(errorCode, message, timestamp, path, null);
    }
}

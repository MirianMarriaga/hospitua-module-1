package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.domain.exception.ErrorCategory;
import org.springframework.http.HttpStatus;

/**
 * Único punto que traduce la categoría de un error de dominio a su código HTTP.
 */
final class ErrorCategoryStatus {

    private ErrorCategoryStatus() {}

    static HttpStatus of(ErrorCategory category) {
        return switch (category) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        };
    }
}

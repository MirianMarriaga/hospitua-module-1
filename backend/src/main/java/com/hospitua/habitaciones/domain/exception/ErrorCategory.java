package com.hospitua.habitaciones.domain.exception;

/**
 * Categoría de un error de dominio. La capa web la traduce a un código HTTP; el dominio no conoce HTTP.
 */
public enum ErrorCategory {
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    INVALID_REQUEST,
    UNAUTHORIZED
}

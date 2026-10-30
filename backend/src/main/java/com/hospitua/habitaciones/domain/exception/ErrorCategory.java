package com.hospitua.habitaciones.domain.exception;

/**
 * Categoría de un error de dominio. La capa web la traduce a un código HTTP; el dominio no conoce HTTP.
 */
public enum ErrorCategory {
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    INVALID_REQUEST,
    UNAUTHORIZED,
    /**
     * Un módulo del que depende la operación no respondió y la operación no puede continuar (424). La liquidación de
     * Módulo 3 no la usa: es informativa y responde 200 con UNAVAILABLE.
     */
    DEPENDENCY_FAILED
}

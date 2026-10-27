package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando falta el encabezado X-User o la cuenta no existe o está desactivada
 * (HTTP 400 CURRENT_USER_REQUIRED).
 */
public class CurrentUserRequiredException extends DomainException {

    public CurrentUserRequiredException() {
        super("Tu sesión terminó. Inicia sesión de nuevo.");
    }

    public CurrentUserRequiredException(String message) {
        super(message);
    }
}

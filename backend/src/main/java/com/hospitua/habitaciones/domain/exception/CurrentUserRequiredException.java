package com.hospitua.habitaciones.domain.exception;

/**
 * Falta X-User, o el usuario no existe o está desactivado (400 CURRENT_USER_REQUIRED, regla 14).
 */
public final class CurrentUserRequiredException extends DomainException {

    public CurrentUserRequiredException() {
        this("Tu sesión terminó. Inicia sesión de nuevo.");
    }

    public CurrentUserRequiredException(String message) {
        super(DomainError.CURRENT_USER_REQUIRED, message);
    }
}

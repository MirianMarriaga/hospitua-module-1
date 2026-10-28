package com.hospitua.habitaciones.domain.exception;

/**
 * Usuario inexistente, contraseña incorrecta o cuenta desactivada (401 INVALID_CREDENTIALS).
 */
public final class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        this("Usuario o contraseña incorrectos.");
    }

    public InvalidCredentialsException(String message) {
        super(DomainError.INVALID_CREDENTIALS, message);
    }
}

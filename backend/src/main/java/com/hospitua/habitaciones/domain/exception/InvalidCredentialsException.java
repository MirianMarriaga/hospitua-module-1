package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando las credenciales de inicio de sesión son incorrectas o la cuenta está desactivada
 * (HTTP 401 INVALID_CREDENTIALS).
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Usuario o contraseña incorrectos.");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

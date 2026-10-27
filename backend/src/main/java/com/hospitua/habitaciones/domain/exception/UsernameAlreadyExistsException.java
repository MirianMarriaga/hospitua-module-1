package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando se intenta registrar un usuario que ya existe en el sistema (HTTP 409 USERNAME_ALREADY_EXISTS).
 */
public class UsernameAlreadyExistsException extends DomainException {

    private final String username;

    public UsernameAlreadyExistsException(String username) {
        super("Ya existe una cuenta con el usuario " + username + ".");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}

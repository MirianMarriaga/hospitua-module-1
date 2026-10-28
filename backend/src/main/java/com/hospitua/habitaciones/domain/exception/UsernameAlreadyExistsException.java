package com.hospitua.habitaciones.domain.exception;

/**
 * Ya existe una cuenta con el usuario indicado (409 USERNAME_ALREADY_EXISTS).
 */
public final class UsernameAlreadyExistsException extends DomainException {

    public UsernameAlreadyExistsException(String username) {
        super(DomainError.USERNAME_ALREADY_EXISTS, "Ya existe una cuenta con el usuario " + username + ".");
    }
}

package com.hospitua.habitaciones.domain.exception;

/**
 * La cuenta está desactivada: no se puede editar ni volver a desactivar (409 ACCOUNT_INACTIVE;
 * plan-gestionar-personal.md). Cada operación pone su mensaje.
 */
public final class AccountInactiveException extends DomainException {

    public AccountInactiveException(String message) {
        super(DomainError.ACCOUNT_INACTIVE, message);
    }
}

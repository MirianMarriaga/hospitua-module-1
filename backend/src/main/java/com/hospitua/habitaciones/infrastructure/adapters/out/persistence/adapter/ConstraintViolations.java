package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Reconoce qué restricción o índice único de PostgreSQL rechazó una escritura, para traducirlo a un error de dominio. */
final class ConstraintViolations {

    private ConstraintViolations() {}

    static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}

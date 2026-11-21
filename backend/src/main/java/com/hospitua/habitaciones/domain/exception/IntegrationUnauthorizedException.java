package com.hospitua.habitaciones.domain.exception;

/**
 * Consulta de otro módulo sin la llave de integración de su cliente, o con una llave incorrecta (401
 * INTEGRATION_UNAUTHORIZED; plan de integración, Fase 1). No dice qué falló, para no orientar a quien prueba llaves.
 */
public final class IntegrationUnauthorizedException extends DomainException {

    public IntegrationUnauthorizedException() {
        super(DomainError.INTEGRATION_UNAUTHORIZED, "La consulta requiere una llave de integración válida.");
    }
}

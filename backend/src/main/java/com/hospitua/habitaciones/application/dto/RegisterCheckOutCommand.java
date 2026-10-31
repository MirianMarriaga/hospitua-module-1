package com.hospitua.habitaciones.application.dto;

import java.util.UUID;

/**
 * Confirmación del Check-Out (plan-registrar-check-out.md, Endpoint 2): la estancia del Paso 1, la casilla del Paso 4
 * y el recepcionista, que es el usuario actual (regla 14 del plan base).
 */
public record RegisterCheckOutCommand(UUID stayId, boolean confirmed, String receptionistId) {}

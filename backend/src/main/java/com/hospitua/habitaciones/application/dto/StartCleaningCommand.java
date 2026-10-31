package com.hospitua.habitaciones.application.dto;

import java.util.UUID;

/**
 * Inicio de la limpieza de una habitación (plan-marcar-habitacion-en-limpieza.md, Structure Decision): {@code memberId}
 * es el usuario actual (regla 14 del plan base). No lleva fechas: la marca de inicio la pone el servidor (FR-006).
 */
public record StartCleaningCommand(UUID roomId, String memberId) {}

package com.hospitua.habitaciones.application.dto;

import java.util.UUID;

/**
 * Inicio de las reparaciones de una habitación (plan-confirmar-reparacion-finalizada.md T010): {@code memberId} es el
 * usuario actual (regla 14 del plan base). No lleva fechas: la marca de inicio la pone el servidor (FR-011).
 */
public record StartRepairCommand(UUID roomId, String memberId) {}

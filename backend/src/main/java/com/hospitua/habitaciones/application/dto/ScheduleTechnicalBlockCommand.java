package com.hospitua.habitaciones.application.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos de una programación de bloqueo técnico (plan-programar-bloqueo-tecnico-para-habitacion.md):
 * {@code memberId} es el {@code username} del usuario actual, que la programa.
 */
public record ScheduleTechnicalBlockCommand(
        UUID roomId, String memberId, String reason, LocalDate startDate, LocalDate estimatedEndDate) {}

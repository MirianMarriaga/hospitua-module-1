package com.hospitua.habitaciones.application.ports.in;

import java.time.LocalDate;

/** Aviso diario de las 00:00 a Módulo 2 (plan-programar-bloqueo-tecnico-para-habitacion.md T025; FR-020). */
public interface PublishDailyMaintenanceNoticesUseCase {

    /**
     * Encola un aviso {@code SCHEDULED} con inicio y fin {@code today} por cada habitación {@code DisabledForRepairs} y
     * por cada informe {@code Applied} vencido: la habitación sigue sin poder asignarse hoy.
     */
    void publishDaily(LocalDate today);
}

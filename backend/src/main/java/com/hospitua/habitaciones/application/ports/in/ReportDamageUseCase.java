package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.DamageReportCreatedDto;
import com.hospitua.habitaciones.application.dto.ReportDamageCommand;

/** Marcar habitación inhabilitada por reparaciones (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T012). */
public interface ReportDamageUseCase {

    /**
     * Con la habitación bloqueada: la pasa de {@code Available} a {@code DisabledForRepairs}, registra el periodo,
     * crea el reporte con la misma marca y encola el aviso {@code SCHEDULED} a Módulo 2. Abre su transacción o se une
     * a la del invocador (fin de limpieza con daño); cualquier rechazo o falla revierte todo.
     *
     * @throws com.hospitua.habitaciones.domain.exception.InvalidDamageDescriptionException si la descripción no es
     *     válida
     * @throws com.hospitua.habitaciones.domain.exception.RoomNotFoundException si la habitación no existe
     * @throws com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException si no está {@code Available}
     */
    DamageReportCreatedDto reportDamage(ReportDamageCommand command);
}

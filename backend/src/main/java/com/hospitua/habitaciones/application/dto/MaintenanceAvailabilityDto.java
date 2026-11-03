package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.MaintenanceConflict;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de la consulta de mantenimientos (plan-consultar-informacion-mantenimientos.md): {@code available} es
 * {@code true} solo si no hay cruces, y {@code conflicts} trae el inicio y el fin de cada uno, ordenados por inicio, sin
 * la justificación ni otros datos del mantenimiento (FR-002). Es el contrato con Módulo 2, así que no expone el
 * {@link MaintenanceConflict} del dominio: renombrarlo no cambia la respuesta.
 */
public record MaintenanceAvailabilityDto(UUID roomId, boolean available, List<ConflictDto> conflicts) {

    public MaintenanceAvailabilityDto {
        conflicts = List.copyOf(conflicts);
    }

    public static MaintenanceAvailabilityDto of(UUID roomId, List<MaintenanceConflict> conflicts) {
        return new MaintenanceAvailabilityDto(
                roomId,
                conflicts.isEmpty(),
                conflicts.stream().map(ConflictDto::of).toList());
    }

    /** Un cruce: {@code maintenanceStart} y {@code maintenanceEnd}, los nombres acordados con Módulo 2. */
    public record ConflictDto(LocalDate maintenanceStart, LocalDate maintenanceEnd) {

        static ConflictDto of(MaintenanceConflict conflict) {
            return new ConflictDto(conflict.maintenanceStart(), conflict.maintenanceEnd());
        }
    }
}

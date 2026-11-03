package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Consultar Información de Mantenimientos (plan-consultar-informacion-mantenimientos.md): si la habitación está
 * disponible frente a los mantenimientos en los días {@code [firstDay, lastDay]}, ambos incluidos. Lo usan Módulo 2 por
 * REST y la programación del bloqueo técnico. Solo lectura (FR-004, FR-011).
 */
public interface ConsultMaintenanceAvailabilityUseCase {

    /**
     * @throws com.hospitua.habitaciones.domain.exception.InvalidDateRangeException si los días incumplen una regla
     *     (sin consultar el repositorio, FR-009)
     * @throws com.hospitua.habitaciones.domain.exception.RoomNotFoundException si la habitación no existe (FR-012)
     */
    MaintenanceAvailabilityDto consult(UUID roomId, LocalDate firstDay, LocalDate lastDay);
}

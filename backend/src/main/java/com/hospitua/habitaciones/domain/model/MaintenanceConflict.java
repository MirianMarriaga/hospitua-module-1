package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Un mantenimiento que se cruza con los días consultados: su inicio y su fin estimado, sin la justificación
 * (plan-consultar-informacion-mantenimientos.md T001; FR-002, FR-010). El cruce del día actual de FR-014 tiene inicio y
 * fin en la fecha de hoy.
 */
public record MaintenanceConflict(LocalDate maintenanceStart, LocalDate maintenanceEnd) {

    public MaintenanceConflict {
        Objects.requireNonNull(maintenanceStart, "El inicio del mantenimiento no puede ser nulo");
        Objects.requireNonNull(maintenanceEnd, "El fin del mantenimiento no puede ser nulo");
        if (maintenanceEnd.isBefore(maintenanceStart)) {
            throw new IllegalArgumentException("El fin del mantenimiento no puede ser anterior a su inicio");
        }
    }

    /** El rango programado de un informe de bloqueo técnico. */
    public static MaintenanceConflict of(TechnicalBlockReport report) {
        return new MaintenanceConflict(report.getStartDate(), report.getEstimatedEndDate());
    }

    /** El cruce del día actual: la habitación no se puede asignar hoy (FR-014). */
    public static MaintenanceConflict onlyOn(LocalDate day) {
        return new MaintenanceConflict(day, day);
    }
}

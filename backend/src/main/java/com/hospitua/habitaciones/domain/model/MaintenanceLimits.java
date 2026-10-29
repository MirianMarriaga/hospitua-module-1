package com.hospitua.habitaciones.domain.model;

/**
 * Límites de rango y antelación para la programación de mantenimientos (T019).
 *
 * @param maxRangeDays   Límite máximo de duración en días (ej. 90).
 * @param maxAdvanceDays Límite máximo de antelación en días (ej. 365).
 */
public record MaintenanceLimits(int maxRangeDays, int maxAdvanceDays) {

    public MaintenanceLimits {
        if (maxRangeDays <= 0) {
            throw new IllegalArgumentException("maxRangeDays debe ser mayor a 0");
        }
        if (maxAdvanceDays <= 0) {
            throw new IllegalArgumentException("maxAdvanceDays debe ser mayor a 0");
        }
    }
}

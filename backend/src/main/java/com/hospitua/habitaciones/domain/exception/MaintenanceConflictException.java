package com.hospitua.habitaciones.domain.exception;

import com.hospitua.habitaciones.domain.model.MaintenanceConflict;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ya hay un mantenimiento programado o en curso que se cruza con el que se quiere programar (409 MAINTENANCE_CONFLICT,
 * con {@code details.conflicts}; programar-bloqueo-tecnico FR-003, FR-004, esc. 3 y caso límite 2). El mensaje nombra el
 * primer cruce con las fechas en DD-MM-YYYY, como las muestra la interfaz (FR-011); {@code details} los trae todos en
 * ISO.
 */
public final class MaintenanceConflictException extends DomainException {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public MaintenanceConflictException(List<MaintenanceConflict> conflicts) {
        super(DomainError.MAINTENANCE_CONFLICT, message(conflicts), Map.of("conflicts", toDetails(conflicts)));
    }

    private static String message(List<MaintenanceConflict> conflicts) {
        if (conflicts.isEmpty()) {
            throw new IllegalArgumentException("Un conflicto de mantenimiento necesita al menos un cruce");
        }
        MaintenanceConflict first = conflicts.getFirst();
        return "Ya hay un mantenimiento programado o en curso del "
                + DISPLAY_DATE.format(first.maintenanceStart()) + " al "
                + DISPLAY_DATE.format(first.maintenanceEnd()) + ". Elige otras fechas.";
    }

    private static List<Map<String, Object>> toDetails(List<MaintenanceConflict> conflicts) {
        return conflicts.stream()
                .map(conflict -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("maintenanceStart", conflict.maintenanceStart().toString());
                    detail.put("maintenanceEnd", conflict.maintenanceEnd().toString());
                    return detail;
                })
                .toList();
    }
}

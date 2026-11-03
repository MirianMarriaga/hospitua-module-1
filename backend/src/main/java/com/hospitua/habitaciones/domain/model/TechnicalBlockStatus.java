package com.hospitua.habitaciones.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de un informe de bloqueo técnico (plan-programar-bloqueo-tecnico-para-habitacion.md T003). Los nombres son los
 * de la columna {@code status} de V3. {@code Completed} y {@code Expired} son definitivos.
 */
public enum TechnicalBlockStatus {
    Scheduled,
    Applied,
    Completed,
    Expired;

    /** Los que cuentan como cruce para la consulta de mantenimientos: programados o en curso. No se puede modificar. */
    public static final Set<TechnicalBlockStatus> ACTIVE = Set.copyOf(EnumSet.of(Scheduled, Applied));

    boolean canMoveTo(TechnicalBlockStatus target) {
        return switch (this) {
            case Scheduled -> target == Applied || target == Expired;
            case Applied -> target == Completed;
            case Completed, Expired -> false;
        };
    }
}

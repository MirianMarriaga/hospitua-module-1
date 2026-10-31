package com.hospitua.habitaciones.domain.model;

import java.util.Set;

/**
 * Cómo se cerró una tarea de limpieza (plan base, modelo de datos): terminada, con un daño reportado o liberada sin
 * terminar.
 */
public enum CleaningTaskOutcome {
    Completed,
    DamageReported,
    Released;

    private static final Set<CleaningTaskOutcome> COUNTS_AS_CLEANING = Set.of(Completed, DamageReported);

    /**
     * Los cierres que cuentan como "última limpieza" de la habitación: una tarea liberada no la limpió. El índice
     * {@code idx_cleaning_task_last_cleaning} de la migración V3 repite estos valores; si cambian aquí, hay que
     * cambiarlos allí.
     */
    public static Set<CleaningTaskOutcome> countsAsCleaning() {
        return COUNTS_AS_CLEANING;
    }
}

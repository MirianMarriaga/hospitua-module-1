package com.hospitua.habitaciones.domain.model;

/**
 * Cómo se cerró una tarea de reparación (plan base, modelo de datos): reparación terminada o tarea liberada sin
 * terminar, para que otro miembro la retome.
 */
public enum ReparationTaskOutcome {
    Completed,
    Released
}

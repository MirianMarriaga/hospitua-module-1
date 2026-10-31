package com.hospitua.habitaciones.application.ports.in;

/**
 * Liberación de la tarea abierta de un miembro cuya cuenta se desactiva (plan-confirmar-fin-de-limpieza.md T017;
 * FR-014). Sin endpoint: lo invoca la desactivación de Gestionar personal, dentro de su transacción.
 */
public interface ReleaseCleaningTaskOnDeactivationUseCase {

    /**
     * Debe ejecutarse dentro de la transacción de la desactivación ({@code MANDATORY}). Si el miembro no tiene tarea
     * abierta, o ya se cerró al releerla tras el bloqueo, no hace nada. No valida el dueño.
     *
     * @param memberId {@code username} del dueño de la tarea
     * @param actorId {@code username} del Administrador, que queda como actor del periodo
     */
    void releaseOpenTaskOf(String memberId, String actorId);
}

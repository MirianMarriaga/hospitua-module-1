package com.hospitua.habitaciones.application.ports.in;

/**
 * Liberación de la reparación abierta de un miembro cuya cuenta se desactiva (plan-confirmar-reparacion-finalizada.md
 * T023; FR-014, caso límite 5). Sin endpoint: lo invoca la desactivación de Gestionar personal, dentro de su
 * transacción.
 */
public interface ReleaseRepairTaskOnDeactivationUseCase {

    /**
     * Debe ejecutarse dentro de la transacción de la desactivación ({@code MANDATORY}). Si el miembro no tiene tarea
     * abierta, o ya se cerró al releerla tras el bloqueo, no hace nada. No valida el dueño ni registra actor: la
     * habitación no cambia.
     *
     * @param memberId {@code username} del dueño de la tarea
     */
    void releaseOpenTaskOf(String memberId);
}

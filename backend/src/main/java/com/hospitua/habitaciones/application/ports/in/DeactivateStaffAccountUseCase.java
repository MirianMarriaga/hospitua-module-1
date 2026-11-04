package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.StaffAccountDto;

/** Desactivar una cuenta del personal (plan-gestionar-personal.md T034; FR-009 a FR-011, FR-013). */
public interface DeactivateStaffAccountUseCase {

    /**
     * Desactiva la cuenta sin borrarla y, en la misma transacción, libera sus tareas abiertas de limpieza y de
     * reparación.
     *
     * @param username usuario de la cuenta (se normaliza)
     * @param administratorId usuario actual, responsable de la desactivación
     * @return la cuenta con {@code active = false}
     * @throws com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException si no existe
     * @throws com.hospitua.habitaciones.domain.exception.AccountInactiveException si ya estaba desactivada
     * @throws com.hospitua.habitaciones.domain.exception.CannotDeactivateSelfException si es la cuenta del responsable
     * @throws com.hospitua.habitaciones.domain.exception.LastAdministratorException si es la última cuenta activa de
     *     Administración
     */
    StaffAccountDto deactivate(String username, String administratorId);
}

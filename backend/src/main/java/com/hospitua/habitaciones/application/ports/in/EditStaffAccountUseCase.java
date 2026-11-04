package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.EditStaffAccountCommand;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;

/** Editar una cuenta del personal (plan-gestionar-personal.md T027; FR-006, FR-008, FR-013). */
public interface EditStaffAccountUseCase {

    /**
     * Con la cuenta bloqueada, cambia el nombre, el área y, si llega, la contraseña de una cuenta activa.
     *
     * @return la cuenta editada con su {@code version} nueva
     * @throws com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException si no existe
     * @throws com.hospitua.habitaciones.domain.exception.AccountInactiveException si está desactivada
     * @throws com.hospitua.habitaciones.domain.exception.ConcurrentStaffUpdateException si la {@code version} ya cambió
     */
    StaffAccountDto edit(EditStaffAccountCommand command);
}

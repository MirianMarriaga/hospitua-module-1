package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.StaffAccountDto;

/** Una cuenta del personal, para el formulario de edición y la confirmación de desactivación (T021; FR-008, FR-009). */
public interface GetStaffAccountUseCase {

    /**
     * La cuenta con su {@code version}; el usuario se normaliza antes de buscarlo.
     *
     * @throws com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException si no existe
     */
    StaffAccountDto get(String username);
}

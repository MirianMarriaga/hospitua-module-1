package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.dto.StaffAccountFilter;

/** Lista del personal (plan-gestionar-personal.md T021; FR-007). */
public interface ListStaffAccountsUseCase {

    /** Cuentas activas y desactivadas que cumplen los filtros, ordenadas por nombre y de 10 en 10. */
    PagedResultDto<StaffAccountDto> list(StaffAccountFilter filter, int page);
}

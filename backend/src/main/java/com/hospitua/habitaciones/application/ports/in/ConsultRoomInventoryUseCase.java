package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import java.util.UUID;

/**
 * Consulta de solo lectura del inventario de habitaciones (plan-consultar-inventario-habitaciones.md, FR-006).
 */
public interface ConsultRoomInventoryUseCase {

    /** Inventario de las vistas del Gerente y del Administrador, paginado desde la página 1 (FR-009). */
    PagedResultDto<RoomDto> search(RoomInventoryFilter filter, int page, int size);

    /** Detalle de una habitación con su {@code version}; lanza {@code RoomNotFoundException} si no existe. */
    RoomDto getById(UUID roomId);
}

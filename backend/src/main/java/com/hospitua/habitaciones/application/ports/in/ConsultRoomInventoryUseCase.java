package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.Module2RoomDto;
import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import java.util.List;
import java.util.UUID;

/**
 * Consulta de solo lectura del inventario de habitaciones (plan-consultar-inventario-habitaciones.md, FR-006).
 */
public interface ConsultRoomInventoryUseCase {

    /** Inventario de las vistas del Gerente y del Administrador, paginado desde la página 1 (FR-009). */
    PagedResultDto<RoomDto> search(RoomInventoryFilter filter, int page, int size);

    /** Detalle de una habitación con su {@code version}; lanza {@code RoomNotFoundException} si no existe. */
    RoomDto getById(UUID roomId);

    /** Habitaciones vendibles de la categoría para Módulo 2, sin las {@code Inactive} y sin paginar (FR-010). */
    List<Module2RoomDto> listSellableByCategory(RoomCategory categoryRoom);

    /**
     * Habitación vendible para Módulo 2; lanza {@code RoomNotFoundException} si no existe o está {@code Inactive}, sin
     * distinguir los dos casos (FR-011).
     */
    Module2RoomDto getSellableById(UUID roomId);
}

package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;

/** Consulta de solo lectura del historial de estados (plan-consultar-historial-de-estados.md, FR-005). */
public interface ConsultRoomStateHistoryUseCase {

    /**
     * Periodos que cumplen los filtros, en orden cronológico y de 15 en 15 desde la página 1 (FR-002, FR-008); lanza
     * {@code InvalidDateRangeException} si "Desde" es posterior a "Hasta" (FR-006).
     */
    PagedResultDto<RoomStateHistoryRowDto> search(RoomStateHistoryFilter filter, int page);
}

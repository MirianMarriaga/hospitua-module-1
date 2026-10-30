package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.domain.model.RoomStateHistory;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia del historial de estados de habitación.
 */
public interface RoomStateHistoryRepositoryPort {

    /** Periodos por página del historial (consultar-historial FR-008). */
    int PAGE_SIZE = 15;

    RoomStateHistory save(RoomStateHistory history);

    Optional<RoomStateHistory> findOpenByRoomId(UUID roomId);

    /**
     * Página {@code page} (desde 1, de {@link #PAGE_SIZE}) de los periodos que cumplen los filtros, con el número y el
     * tipo actual de la habitación y el nombre del responsable; orden por inicio, número, fin (abiertos al final) e id
     * (consultar-historial T002).
     */
    PagedResultDto<RoomStateHistoryRowDto> search(RoomStateHistoryFilter filter, int page);
}

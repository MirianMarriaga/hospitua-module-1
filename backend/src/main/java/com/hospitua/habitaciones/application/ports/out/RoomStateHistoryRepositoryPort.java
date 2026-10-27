package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.RoomStateHistory;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia del historial de estados de habitación.
 */
public interface RoomStateHistoryRepositoryPort {

    RoomStateHistory save(RoomStateHistory history);

    Optional<RoomStateHistory> findOpenByRoomId(UUID roomId);
}

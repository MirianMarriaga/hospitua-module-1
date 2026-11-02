package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.RoomDecommissionInfoDto;
import com.hospitua.habitaciones.application.ports.in.GetRoomDecommissionUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmación de la reactivación (plan-marcar-habitacion-como-disponible.md T018): la habitación y su baja vigente.
 * Toda habitación {@code Inactive} tiene una, también las que la semilla carga inactivas.
 */
@Service
@RequiredArgsConstructor
public class RoomDecommissionQueryService implements GetRoomDecommissionUseCase {

    private final RoomRepositoryPort roomRepository;
    private final RoomDecommissionRepositoryPort decommissionRepository;

    @Override
    @Transactional(readOnly = true)
    public RoomDecommissionInfoDto getCurrent(UUID roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);
        ensureInactive(room.getStatus());
        RoomDecommission decommission = decommissionRepository
                .findCurrentByRoomId(roomId)
                .orElseThrow(() -> new IllegalStateException("La habitación inactiva " + roomId + " no tiene baja."));
        return RoomDecommissionInfoDto.of(room, decommission);
    }

    /** Texto de las tablas de errores de los dos endpoints de la reactivación (esc. 3; FR-006). */
    static void ensureInactive(RoomStatus status) {
        if (status != RoomStatus.Inactive) {
            throw new InvalidRoomTransitionException(
                    status,
                    "La habitación no está Inactiva (estado actual: " + status.displayName()
                            + "); no es posible marcarla como disponible.");
        }
    }
}

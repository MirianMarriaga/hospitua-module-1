package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.DecommissionRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDecommissionDto;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transacción de la baja (plan-dar-de-baja-habitacion.md T015). Es un bean aparte de {@link DecommissionRoomService}
 * porque una llamada interna a un método {@code @Transactional} no pasa por el proxy de Spring y no abriría la
 * transacción. Bloquea la fila como primera carga (regla 13 del plan base): si la habitación cambió de estado mientras
 * Módulo 2 respondía, la transición a {@code Inactive} la rechaza y no se registra nada.
 */
@Component
@RequiredArgsConstructor
class RoomDecommissionRegistration {

    private final RoomRepositoryPort roomRepository;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;
    private final RoomDecommissionRepositoryPort decommissionRepository;

    @Transactional
    public RoomDecommissionDto register(DecommissionRoomCommand command, String reasonDetail) {
        Room room = roomRepository.lockById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        OffsetDateTime decommissionDateTime = transitionToInactive(room, command.administratorId());
        RoomDecommission decommission = decommissionRepository.save(RoomDecommission.decommission(
                room.getId(), command.reasonCategory(), reasonDetail, command.administratorId(), decommissionDateTime));
        return RoomDecommissionDto.of(room, decommission);
    }

    /** La marca del periodo {@code Inactive} del historial es también la de la baja (FR-007, FR-011). */
    private OffsetDateTime transitionToInactive(Room room, String administratorId) {
        try {
            return transitionRoomStateUseCase.transition(
                    room.getId(), RoomStatus.Inactive, administratorId, SourceFlow.DECOMMISSION_ROOM, null);
        } catch (InvalidRoomTransitionException ex) {
            throw new InvalidRoomTransitionException(
                    ex.getCurrentStatus(),
                    "La habitación cambió de estado (estado actual: "
                            + ex.getCurrentStatus().displayName() + "); no se dio de baja.");
        }
    }
}

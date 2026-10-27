package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación que orquesta la validación de máquina de estados y el registro
 * atómico del historial para las transiciones de estado de una habitación.
 */
@Service
@RequiredArgsConstructor
public class RoomStateTransitionService implements TransitionRoomStateUseCase {

    private final RoomRepositoryPort roomRepositoryPort;
    private final RoomStateHistoryRecorder historyRecorder;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OffsetDateTime transition(
            UUID roomId, RoomStatus target, String actorId, SourceFlow sourceFlow, String reservationRef) {
        if (target == RoomStatus.Reserved && (reservationRef == null || reservationRef.isBlank())) {
            // TODO(pendiente): verificar si se requiere una excepción de dominio dedicada para reservationRef nulo en
            // lugar de IllegalArgumentException
            throw new IllegalArgumentException("La referencia de reserva es obligatoria para apartar la habitación.");
        }

        Room room = roomRepositoryPort.findById(roomId).orElseThrow(RoomNotFoundException::new);
        RoomStatus previousStatus = room.getStatus();

        if (target == RoomStatus.Reserved) {
            room.reserveFor(reservationRef);
        } else {
            room.transitionTo(target);
        }

        roomRepositoryPort.save(room);

        return historyRecorder.recordTransition(roomId, target, previousStatus, actorId, sourceFlow, reservationRef);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OffsetDateTime registerInitialStatus(UUID roomId, String actorId, SourceFlow sourceFlow) {
        Room room = roomRepositoryPort.findById(roomId).orElseThrow(RoomNotFoundException::new);

        return historyRecorder.recordTransition(roomId, room.getStatus(), null, actorId, sourceFlow, null);
    }
}

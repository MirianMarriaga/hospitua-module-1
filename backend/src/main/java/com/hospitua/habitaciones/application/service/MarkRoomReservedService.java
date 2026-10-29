package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedResult;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.MarkRoomReservedOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación para marcar una habitación como reservada.
 */
@Service
@RequiredArgsConstructor
public class MarkRoomReservedService implements MarkRoomReservedUseCase {

    private static final Logger log = LoggerFactory.getLogger(MarkRoomReservedService.class);

    private final RoomRepositoryPort roomRepositoryPort;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;
    private final RoomAuditLogRepositoryPort roomAuditLogRepositoryPort;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MarkRoomReservedResult markReserved(MarkRoomReservedCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("El comando no puede ser nulo");
        }

        Optional<Room> optionalRoom = roomRepositoryPort.findById(command.roomId());
        if (optionalRoom.isEmpty()) {
            log.warn("Habitación no encontrada para marcar como reservada: {}", command.roomId());
            return new MarkRoomReservedResult(MarkRoomReservedOutcome.ROOM_NOT_FOUND, null, null);
        }

        Room room = optionalRoom.get();

        if (room.getStatus() == RoomStatus.Available) {
            transitionRoomStateUseCase.transition(
                    command.roomId(), RoomStatus.Reserved, null, SourceFlow.MARK_RESERVED, command.reservationRef());
            return new MarkRoomReservedResult(
                    MarkRoomReservedOutcome.RESERVED, RoomStatus.Reserved, command.reservationRef());
        }

        if (room.getStatus() == RoomStatus.Reserved
                && Objects.equals(room.getReservedByReservationRef(), command.reservationRef())) {
            return new MarkRoomReservedResult(
                    MarkRoomReservedOutcome.ALREADY_RESERVED, RoomStatus.Reserved, room.getReservedByReservationRef());
        }

        Map<String, Object> details = new HashMap<>();
        details.put("currentStatus", room.getStatus().name());
        details.put("incomingReservationRef", command.reservationRef());
        if (room.getReservedByReservationRef() != null) {
            details.put("linkedReservationRef", room.getReservedByReservationRef());
        }
        details.put("trigger", command.trigger().name());

        roomAuditLogRepositoryPort.record(
                new RoomAuditEntry(room.getId(), RoomAuditEventType.RESERVATION_STATE_CONFLICT, null, details));

        return new MarkRoomReservedResult(
                MarkRoomReservedOutcome.CONFLICT, room.getStatus(), room.getReservedByReservationRef());
    }
}

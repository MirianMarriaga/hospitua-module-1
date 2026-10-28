package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableResult;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación que implementa el puerto transversal MarkRoomAvailableUseCase (T018).
 * Valida el origen según el sourceFlow y delega la transición y el registro de historial
 * en TransitionRoomStateUseCase dentro de la transacción obligatoria del invocador (MANDATORY).
 */
@Service
@RequiredArgsConstructor
public class MarkRoomAvailableService implements MarkRoomAvailableUseCase {

    private final RoomRepositoryPort roomRepositoryPort;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MarkRoomAvailableResult markAvailable(MarkRoomAvailableCommand command) {
        Objects.requireNonNull(command, "El comando no puede ser nulo");
        Objects.requireNonNull(command.roomId(), "El id de la habitación no puede ser nulo");
        Objects.requireNonNull(command.sourceFlow(), "El flujo de origen no puede ser nulo");

        Room room = roomRepositoryPort.findById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        RoomStatus currentStatus = room.getStatus();

        if (command.sourceFlow() == SourceFlow.MARK_AVAILABLE) {
            if (currentStatus == RoomStatus.Inactive) {
                if (command.actorId() == null || command.actorId().isBlank()) {
                    throw new IllegalArgumentException(
                            "El actorId es obligatorio para reactivar una habitación inactiva.");
                }
            } else if (currentStatus == RoomStatus.Reserved) {
                if (command.reservationRef() == null || command.reservationRef().isBlank()) {
                    throw new IllegalArgumentException(
                            "La referencia de reserva es obligatoria para liberar una habitación reservada.");
                }
                if (!Objects.equals(room.getReservedByReservationRef(), command.reservationRef())) {
                    throw new InvalidRoomTransitionException(
                            currentStatus,
                            "La habitación está apartada para otra reserva (" + room.getReservedByReservationRef()
                                    + ").");
                }
            } else {
                throw new InvalidRoomTransitionException(
                        currentStatus,
                        "La habitación no está Inactiva ni Reservada (estado actual: " + currentStatus
                                + "); no es posible marcarla como disponible.");
            }
        } else if (command.sourceFlow() == SourceFlow.CONFIRM_CLEANING_END) {
            if (currentStatus != RoomStatus.InCleaning) {
                throw new InvalidRoomTransitionException(
                        currentStatus,
                        "La habitación no está En limpieza (estado actual: " + currentStatus
                                + "); no es posible confirmar fin de limpieza.");
            }
        } else {
            throw new InvalidRoomTransitionException(
                    currentStatus, "Flujo de origen no permitido para marcar como disponible: " + command.sourceFlow());
        }

        OffsetDateTime transitionDateTime = transitionRoomStateUseCase.transition(
                command.roomId(),
                RoomStatus.Available,
                command.actorId(),
                command.sourceFlow(),
                command.reservationRef());

        return new MarkRoomAvailableResult(command.roomId(), currentStatus, transitionDateTime);
    }
}

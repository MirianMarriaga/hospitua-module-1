package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningResult;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación para marcar una habitación como pendiente a limpieza.
 */
@Service
@RequiredArgsConstructor
public class MarkPendingCleaningService implements MarkPendingCleaningUseCase {

    private final RoomRepositoryPort roomRepositoryPort;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MarkPendingCleaningResult markPendingCleaning(MarkPendingCleaningCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("El comando no puede ser nulo");
        }

        Room room = roomRepositoryPort.findById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        RoomStatus previousStatus = room.getStatus();

        if (previousStatus == RoomStatus.InCleaning && command.sourceFlow() != SourceFlow.RELEASE_CLEANING_TASK) {
            throw new InvalidRoomTransitionException(
                    previousStatus,
                    "La transición desde InCleaning a PendingCleaning solo se permite mediante RELEASE_CLEANING_TASK");
        }

        OffsetDateTime transitionDateTime = transitionRoomStateUseCase.transition(
                command.roomId(), RoomStatus.PendingCleaning, command.actorId(), command.sourceFlow(), null);

        return new MarkPendingCleaningResult(command.roomId(), previousStatus, transitionDateTime);
    }
}

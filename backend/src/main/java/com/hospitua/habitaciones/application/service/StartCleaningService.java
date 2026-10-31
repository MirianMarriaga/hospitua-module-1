package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.CleaningTaskStartedDto;
import com.hospitua.habitaciones.application.dto.StartCleaningCommand;
import com.hospitua.habitaciones.application.ports.in.StartCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marcar habitación en limpieza (plan-marcar-habitacion-en-limpieza.md T012). Bloquea la fila de la habitación como
 * primera carga de la transacción (regla 13 del plan base): de dos miembros que inician la misma habitación a la vez,
 * el segundo espera y la encuentra en {@code InCleaning}. La validación del estado es la de la transición; el servicio
 * solo le pone el mensaje del plan.
 */
@Service
@RequiredArgsConstructor
public class StartCleaningService implements StartCleaningUseCase {

    private final RoomRepositoryPort roomRepository;
    private final CleaningTaskRepositoryPort cleaningTaskRepository;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Override
    @Transactional
    public CleaningTaskStartedDto startCleaning(StartCleaningCommand command) {
        Room room = roomRepository.lockById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        if (cleaningTaskRepository.findOpenByMember(command.memberId()).isPresent()) {
            throw new ActiveTaskExistsException(
                    "Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra.");
        }
        OffsetDateTime startDateTime = transitionToInCleaning(room, command.memberId());
        CleaningTask task =
                cleaningTaskRepository.save(CleaningTask.start(room.getId(), command.memberId(), startDateTime));
        return new CleaningTaskStartedDto(task.getId());
    }

    /** La marca del periodo abierto en el historial es también la de inicio de la tarea (FR-006, FR-008). */
    private OffsetDateTime transitionToInCleaning(Room room, String memberId) {
        try {
            return transitionRoomStateUseCase.transition(
                    room.getId(), RoomStatus.InCleaning, memberId, SourceFlow.START_CLEANING, null);
        } catch (InvalidRoomTransitionException ex) {
            throw new InvalidRoomTransitionException(
                    ex.getCurrentStatus(),
                    "La habitación está " + ex.getCurrentStatus().displayName() + "; ahora no se puede limpiar.");
        }
    }
}

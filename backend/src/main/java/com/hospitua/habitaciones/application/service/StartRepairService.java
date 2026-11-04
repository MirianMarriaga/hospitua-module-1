package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.RepairTaskStartedDto;
import com.hospitua.habitaciones.application.dto.StartRepairCommand;
import com.hospitua.habitaciones.application.ports.in.StartRepairUseCase;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Iniciar reparaciones (plan-confirmar-reparacion-finalizada.md T010). Bloquea la fila de la habitación como primera
 * carga de la transacción (regla 13 del plan base): de dos miembros que inician la misma habitación a la vez, el
 * segundo espera y encuentra la tarea del primero ({@code ROOM_TASK_EXISTS}). No hay transición de estado: la marca de
 * inicio sale del {@link Clock} y el historial no se toca (FR-015).
 */
@Service
@RequiredArgsConstructor
public class StartRepairService implements StartRepairUseCase {

    private static final Set<RoomStatus> REPAIRABLE = Set.of(RoomStatus.DisabledForRepairs, RoomStatus.TechnicalBlock);

    private final RoomRepositoryPort roomRepository;
    private final ReparationTaskRepositoryPort reparationTaskRepository;
    private final Clock clock;

    @Override
    @Transactional
    public RepairTaskStartedDto startRepair(StartRepairCommand command) {
        Room room = roomRepository.lockById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        if (reparationTaskRepository.findOpenByMember(command.memberId()).isPresent()) {
            throw new ActiveTaskExistsException(
                    "Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra.");
        }
        if (!REPAIRABLE.contains(room.getStatus())) {
            throw new InvalidRoomTransitionException(
                    room.getStatus(),
                    "La habitación está " + room.getStatus().displayName() + "; no necesita reparación.");
        }
        if (reparationTaskRepository.existsOpenByRoom(room.getId())) {
            throw new RoomTaskExistsException("Otro compañero ya está reparando esta habitación.");
        }
        ReparationTask task = reparationTaskRepository.save(
                ReparationTask.start(room.getId(), command.memberId(), OffsetDateTime.now(clock)));
        return new RepairTaskStartedDto(task.getId());
    }
}

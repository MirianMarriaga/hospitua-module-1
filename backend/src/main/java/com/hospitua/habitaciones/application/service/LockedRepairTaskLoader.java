package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.TaskNotFoundException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pasos 1 a 4 de confirmar y liberar una reparación (plan-confirmar-reparacion-finalizada.md T022, T023). Lee solo la
 * habitación de la tarea, la bloquea como primera carga de la transacción (regla 13 del plan base) y relee la tarea ya
 * fresca: de dos cierres simultáneos, el segundo espera el bloqueo y la encuentra cerrada (FR-018).
 */
@Component
@RequiredArgsConstructor
class LockedRepairTaskLoader {

    private final ReparationTaskRepositoryPort reparationTaskRepository;
    private final RoomRepositoryPort roomRepository;

    /** La tarea abierta del miembro con su habitación bloqueada; debe llamarse dentro de una transacción. */
    LockedRepairTask loadClosableBy(UUID taskId, String memberId) {
        UUID roomId = reparationTaskRepository.findRoomIdByTaskId(taskId).orElseThrow(LockedRepairTaskLoader::notFound);
        Room room = roomRepository.lockById(roomId).orElseThrow(RoomNotFoundException::new);
        ReparationTask task = reparationTaskRepository.findById(taskId).orElseThrow(LockedRepairTaskLoader::notFound);
        task.ensureClosableBy(memberId);
        return new LockedRepairTask(room, task);
    }

    private static TaskNotFoundException notFound() {
        return new TaskNotFoundException("No encontramos esta reparación. Vuelve al panel.");
    }

    /** La habitación tal como quedó al bloquearla y su tarea abierta. */
    record LockedRepairTask(Room room, ReparationTask task) {}
}

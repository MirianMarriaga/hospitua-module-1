package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.TaskNotFoundException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pasos 1 a 4 de confirmar y liberar una tarea (plan-confirmar-fin-de-limpieza.md T016, T017). Lee solo la habitación
 * de la tarea, la bloquea como primera carga de la transacción (regla 13 del plan base) y relee la tarea ya fresca: de
 * dos cierres simultáneos, el segundo espera el bloqueo y la encuentra cerrada (FR-016).
 */
@Component
@RequiredArgsConstructor
class LockedCleaningTaskLoader {

    private final CleaningTaskRepositoryPort cleaningTaskRepository;
    private final RoomRepositoryPort roomRepository;

    /** La tarea abierta del miembro, con su habitación bloqueada; debe llamarse dentro de una transacción. */
    CleaningTask loadClosableBy(UUID taskId, String memberId) {
        UUID roomId = cleaningTaskRepository.findRoomIdByTaskId(taskId).orElseThrow(LockedCleaningTaskLoader::notFound);
        roomRepository.lockById(roomId).orElseThrow(RoomNotFoundException::new);
        CleaningTask task = cleaningTaskRepository.findById(taskId).orElseThrow(LockedCleaningTaskLoader::notFound);
        task.ensureClosableBy(memberId);
        return task;
    }

    private static TaskNotFoundException notFound() {
        return new TaskNotFoundException("No encontramos esta limpieza. Vuelve al panel.");
    }
}

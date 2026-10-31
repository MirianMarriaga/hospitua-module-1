package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningResult;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liberar tarea (plan-confirmar-fin-de-limpieza.md T017): el mismo flujo para el endpoint, que libera la tarea de su
 * dueño, y para la desactivación de una cuenta, que libera la tarea abierta del miembro con el Administrador como
 * actor. La habitación vuelve a {@code PendingCleaning} y la tarea se cierra con la marca de esa transición.
 */
@Service
@RequiredArgsConstructor
public class ReleaseCleaningTaskService
        implements ReleaseCleaningTaskUseCase, ReleaseCleaningTaskOnDeactivationUseCase {

    private final LockedCleaningTaskLoader lockedCleaningTaskLoader;
    private final CleaningTaskRepositoryPort cleaningTaskRepository;
    private final RoomRepositoryPort roomRepository;
    private final MarkPendingCleaningUseCase markPendingCleaningUseCase;

    @Override
    @Transactional
    public void releaseTask(UUID taskId, String memberId) {
        release(lockedCleaningTaskLoader.loadClosableBy(taskId, memberId), memberId);
    }

    /**
     * Tras el bloqueo vuelve a buscar la tarea abierta del miembro en la base: la primera lectura pudo quedar vieja si
     * otra solicitud la cerró mientras se esperaba el bloqueo.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseOpenTaskOf(String memberId, String actorId) {
        Optional<CleaningTask> openTask = cleaningTaskRepository.findOpenByMember(memberId);
        if (openTask.isEmpty()) {
            return;
        }
        UUID taskId = openTask.get().getId();
        roomRepository.lockById(openTask.get().getRoomId()).orElseThrow(RoomNotFoundException::new);
        cleaningTaskRepository
                .findOpenByMember(memberId)
                .filter(task -> task.getId().equals(taskId))
                .ifPresent(task -> release(task, actorId));
    }

    private void release(CleaningTask task, String actorId) {
        MarkPendingCleaningResult pendingCleaning = markPendingCleaningUseCase.markPendingCleaning(
                new MarkPendingCleaningCommand(task.getRoomId(), SourceFlow.RELEASE_CLEANING_TASK, actorId));
        task.close(CleaningTaskOutcome.Released, pendingCleaning.transitionDateTime());
        cleaningTaskRepository.save(task);
    }
}

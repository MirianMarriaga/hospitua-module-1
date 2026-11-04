package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.ReleaseRepairTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseRepairTaskUseCase;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.ReparationTaskOutcome;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liberar reparación (plan-confirmar-reparacion-finalizada.md T023): el mismo cierre para el endpoint, que libera la
 * tarea de su dueño, y para la desactivación de una cuenta. Ninguno cambia la habitación, su informe ni el historial, ni
 * publica avisos: la tarea solo se cierra con {@code Released} a la hora del {@link Clock} (FR-016).
 */
@Service
@RequiredArgsConstructor
public class ReleaseRepairTaskService implements ReleaseRepairTaskUseCase, ReleaseRepairTaskOnDeactivationUseCase {

    private final LockedRepairTaskLoader lockedRepairTaskLoader;
    private final ReparationTaskRepositoryPort reparationTaskRepository;
    private final RoomRepositoryPort roomRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void releaseTask(UUID taskId, String memberId) {
        release(lockedRepairTaskLoader.loadClosableBy(taskId, memberId).task());
    }

    /**
     * Tras el bloqueo vuelve a buscar la tarea abierta del miembro en la base: la primera lectura pudo quedar vieja si
     * otra solicitud la cerró mientras se esperaba el bloqueo. Solo libera esa misma tarea; otra que el miembro haya
     * abierto entretanto está en otra habitación, sin bloquear.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseOpenTaskOf(String memberId) {
        Optional<ReparationTask> openTask = reparationTaskRepository.findOpenByMember(memberId);
        if (openTask.isEmpty()) {
            return;
        }
        UUID taskId = openTask.get().getId();
        roomRepository.lockById(openTask.get().getRoomId()).orElseThrow(RoomNotFoundException::new);
        reparationTaskRepository
                .findOpenByMember(memberId)
                .filter(task -> task.getId().equals(taskId))
                .ifPresent(this::release);
    }

    private void release(ReparationTask task) {
        task.close(ReparationTaskOutcome.Released, OffsetDateTime.now(clock));
        reparationTaskRepository.save(task);
    }
}

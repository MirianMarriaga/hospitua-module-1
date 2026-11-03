package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.ReparationTaskMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataReparationTaskJpaRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia de las tareas de reparación (plan-confirmar-reparacion-finalizada.md T002). Guarda con
 * {@code saveAndFlush} para que un índice único de tareas abiertas falle aquí y se traduzca a su error de dominio, y no
 * al confirmar la transacción.
 */
@Component
@RequiredArgsConstructor
public class ReparationTaskRepositoryAdapter implements ReparationTaskRepositoryPort {

    static final String OPEN_BY_MEMBER_UNIQUE = "uk_reparation_task_open_by_member";
    static final String OPEN_BY_ROOM_UNIQUE = "uk_reparation_task_open_by_room";

    private final SpringDataReparationTaskJpaRepository reparationTaskJpaRepository;
    private final ReparationTaskMapper reparationTaskMapper;

    @Override
    public ReparationTask save(ReparationTask task) {
        try {
            return reparationTaskMapper.toDomain(
                    reparationTaskJpaRepository.saveAndFlush(reparationTaskMapper.toEntity(task)));
        } catch (DataIntegrityViolationException ex) {
            if (ConstraintViolations.violates(ex, OPEN_BY_MEMBER_UNIQUE)) {
                throw new ActiveTaskExistsException();
            }
            if (ConstraintViolations.violates(ex, OPEN_BY_ROOM_UNIQUE)) {
                throw new RoomTaskExistsException();
            }
            throw ex;
        }
    }

    @Override
    public Optional<ReparationTask> findById(UUID taskId) {
        return reparationTaskJpaRepository.findById(taskId).map(reparationTaskMapper::toDomain);
    }

    @Override
    public Optional<UUID> findRoomIdByTaskId(UUID taskId) {
        return reparationTaskJpaRepository.findRoomIdById(taskId);
    }

    @Override
    public Optional<ReparationTask> findOpenByMember(String memberId) {
        return reparationTaskJpaRepository
                .findByMaintenanceStaffMemberIdAndEndDateTimeIsNull(memberId)
                .map(reparationTaskMapper::toDomain);
    }

    @Override
    public boolean existsOpenByRoom(UUID roomId) {
        return reparationTaskJpaRepository.existsByRoomIdAndEndDateTimeIsNull(roomId);
    }

    @Override
    public Set<UUID> findRoomIdsWithOpenTask() {
        return Set.copyOf(reparationTaskJpaRepository.findRoomIdsWithOpenTask());
    }
}

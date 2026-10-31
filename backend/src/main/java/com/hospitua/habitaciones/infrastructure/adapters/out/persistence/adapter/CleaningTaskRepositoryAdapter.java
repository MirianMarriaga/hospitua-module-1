package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.CleaningTaskMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataCleaningTaskJpaRepository;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataCleaningTaskJpaRepository.LastCleaningRow;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia de las tareas de limpieza (plan-marcar-habitacion-en-limpieza.md T002). Guarda con
 * {@code saveAndFlush} para que un índice único de tareas abiertas falle aquí y se traduzca a su error de dominio, y no
 * al confirmar la transacción.
 */
@Component
@RequiredArgsConstructor
public class CleaningTaskRepositoryAdapter implements CleaningTaskRepositoryPort {

    static final String OPEN_BY_MEMBER_UNIQUE = "uk_cleaning_task_open_by_member";
    static final String OPEN_BY_ROOM_UNIQUE = "uk_cleaning_task_open_by_room";

    private final SpringDataCleaningTaskJpaRepository cleaningTaskJpaRepository;
    private final CleaningTaskMapper cleaningTaskMapper;

    @Override
    public CleaningTask save(CleaningTask task) {
        try {
            return cleaningTaskMapper.toDomain(
                    cleaningTaskJpaRepository.saveAndFlush(cleaningTaskMapper.toEntity(task)));
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
    public Optional<CleaningTask> findById(UUID taskId) {
        return cleaningTaskJpaRepository.findById(taskId).map(cleaningTaskMapper::toDomain);
    }

    @Override
    public Optional<UUID> findRoomIdByTaskId(UUID taskId) {
        return cleaningTaskJpaRepository.findRoomIdById(taskId);
    }

    @Override
    public Optional<CleaningTask> findOpenByMember(String memberId) {
        return cleaningTaskJpaRepository
                .findByCleaningStaffMemberIdAndEndDateTimeIsNull(memberId)
                .map(cleaningTaskMapper::toDomain);
    }

    @Override
    public Map<UUID, OffsetDateTime> findLastCleaningEndByRoomIds(
            Collection<UUID> roomIds, Set<CleaningTaskOutcome> countedOutcomes) {
        if (roomIds.isEmpty() || countedOutcomes.isEmpty()) {
            return Map.of();
        }
        return cleaningTaskJpaRepository.findLastCleaningEnds(roomIds, countedOutcomes).stream()
                .collect(Collectors.toMap(LastCleaningRow::getRoomId, LastCleaningRow::getLastCleaningEnd));
    }
}

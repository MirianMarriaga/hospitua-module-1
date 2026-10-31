package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.CleaningPanelDto;
import com.hospitua.habitaciones.application.ports.in.GetCleaningPanelUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panel de limpieza, de solo lectura (plan-consultar-panel-limpieza.md T007; FR-001 a FR-006, FR-011). La última
 * limpieza de todas las habitaciones sale de una sola consulta por lotes, que ignora las tareas {@code Released}
 * (FR-004).
 */
@Service
@RequiredArgsConstructor
public class CleaningPanelQueryService implements GetCleaningPanelUseCase {

    private static final Set<RoomStatus> PANEL_STATUSES = Set.of(RoomStatus.PendingCleaning, RoomStatus.Available);

    /** Primero las pendientes; el orden es estable, así cada grupo conserva el orden natural del puerto (FR-006). */
    private static final Comparator<Room> PENDING_FIRST =
            Comparator.comparing(room -> room.getStatus() != RoomStatus.PendingCleaning);

    private final RoomRepositoryPort roomRepository;
    private final CleaningTaskRepositoryPort cleaningTaskRepository;

    @Override
    @Transactional(readOnly = true)
    public CleaningPanelDto getPanel(String memberId) {
        Optional<CleaningTask> openTask = cleaningTaskRepository.findOpenByMember(memberId);
        if (openTask.isPresent()) {
            return activeTaskPanel(openTask.get());
        }
        List<Room> rooms = roomRepository.findByStatusIn(PANEL_STATUSES).stream()
                .sorted(PENDING_FIRST)
                .toList();
        return CleaningPanelDto.withRooms(rooms, lastCleanings(rooms));
    }

    /** Con tarea abierta el panel no lista habitaciones: el frontend redirige a la vista de tarea activa (FR-001). */
    private CleaningPanelDto activeTaskPanel(CleaningTask task) {
        Room room = roomRepository
                .findById(task.getRoomId())
                .orElseThrow(() -> new IllegalStateException("La tarea " + task.getId() + " no tiene habitación"));
        return CleaningPanelDto.withActiveTask(
                task, room, lastCleanings(List.of(room)).get(room.getId()));
    }

    private Map<UUID, OffsetDateTime> lastCleanings(List<Room> rooms) {
        return cleaningTaskRepository.findLastCleaningEndByRoomIds(
                rooms.stream().map(Room::getId).toList(), CleaningTaskOutcome.countsAsCleaning());
    }
}

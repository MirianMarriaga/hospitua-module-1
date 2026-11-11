package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MaintenancePanelDto;
import com.hospitua.habitaciones.application.dto.MaintenancePanelDto.MaintenanceRangeDto;
import com.hospitua.habitaciones.application.dto.MaintenancePanelDto.MaintenanceRoomDto;
import com.hospitua.habitaciones.application.ports.in.GetMaintenancePanelUseCase;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.time.Clock;
import java.time.LocalDate;
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
 * Panel de mantenimiento, de solo lectura (plan-consultar-panel-mantenimiento.md T008; FR-001 a FR-006, FR-011). Los
 * informes y las tareas abiertas de todas las habitaciones salen de consultas por lotes, no de una por habitación.
 */
@Service
@RequiredArgsConstructor
public class MaintenancePanelQueryService implements GetMaintenancePanelUseCase {

    /**
     * Tres grupos: {@code TechnicalBlock} vencidos, el resto de {@code DisabledForRepairs} y {@code TechnicalBlock}, y
     * las demás. El orden es estable, así cada grupo conserva el orden natural por número del puerto (FR-002, FR-006).
     */
    private static final Comparator<MaintenanceRoomDto> INTERVENTION_FIRST =
            Comparator.comparingInt(MaintenancePanelQueryService::group);

    private final RoomRepositoryPort roomRepository;
    private final ReparationTaskRepositoryPort reparationTaskRepository;
    private final TechnicalBlockReportRepositoryPort technicalBlockReportRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MaintenancePanelDto getPanel(String memberId) {
        Optional<ReparationTask> openTask = reparationTaskRepository.findOpenByMember(memberId);
        if (openTask.isPresent()) {
            return activeTaskPanel(openTask.get());
        }
        List<Room> rooms = roomRepository.findByStatusNot(RoomStatus.Inactive);
        List<MaintenanceRoomDto> rows = rows(rooms, reparationTaskRepository.findRoomIdsWithOpenTask()).stream()
                .sorted(INTERVENTION_FIRST)
                .toList();
        return MaintenancePanelDto.withRooms(rows);
    }

    /** Con tarea abierta el panel no lista habitaciones: el frontend redirige a la vista de tarea activa (FR-001). */
    private MaintenancePanelDto activeTaskPanel(ReparationTask task) {
        Room room = roomRepository
                .findById(task.getRoomId())
                .orElseThrow(() -> new IllegalStateException("La tarea " + task.getId() + " no tiene habitación"));
        return MaintenancePanelDto.withActiveTask(
                task.getId(),
                task.getStartDateTime(),
                rows(List.of(room), Set.of(room.getId())).getFirst());
    }

    private List<MaintenanceRoomDto> rows(List<Room> rooms, Set<UUID> roomsWithOpenTask) {
        List<UUID> roomIds = rooms.stream().map(Room::getId).toList();
        Map<UUID, TechnicalBlockReport> applied = technicalBlockReportRepository.findAppliedByRoomIds(roomIds);
        Map<UUID, TechnicalBlockReport> scheduled =
                technicalBlockReportRepository.findEarliestScheduledByRoomIds(roomIds);
        LocalDate today = LocalDate.now(clock);
        return rooms.stream()
                .map(room -> row(
                        room,
                        applied.get(room.getId()),
                        scheduled.get(room.getId()),
                        roomsWithOpenTask.contains(room.getId()),
                        today))
                .toList();
    }

    /** {@code maintenance}: el {@code Applied} o, si no hay, el primer {@code Scheduled}; los {@code Expired} no cuentan. */
    private static MaintenanceRoomDto row(
            Room room,
            TechnicalBlockReport applied,
            TechnicalBlockReport scheduled,
            boolean hasOpenTask,
            LocalDate today) {
        boolean overdue = room.getStatus() == RoomStatus.TechnicalBlock && applied != null && applied.isOverdue(today);
        TechnicalBlockReport current = applied != null ? applied : scheduled;
        MaintenanceRangeDto maintenance =
                current == null ? null : new MaintenanceRangeDto(current.getStartDate(), current.getEstimatedEndDate());
        return new MaintenanceRoomDto(
                room.getId(),
                room.getRoomNumber(),
                room.getCategoryRoom(),
                room.getStatus(),
                overdue,
                hasOpenTask,
                maintenance);
    }

    private static int group(MaintenanceRoomDto row) {
        if (row.overdue()) {
            return 0;
        }
        return row.status() == RoomStatus.DisabledForRepairs || row.status() == RoomStatus.TechnicalBlock ? 1 : 2;
    }
}

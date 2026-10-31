package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/cleaning/panel} (plan-consultar-panel-limpieza.md T002): la tarea activa del miembro o
 * las habitaciones que puede atender. Con tarea activa, {@code rooms} llega vacío (FR-001). Los nulos se omiten del
 * JSON ({@code default-property-inclusion: non_null}): sin tarea no viaja {@code activeTask}, y una habitación sin
 * limpiezas no lleva {@code lastCleaningDateTime}.
 */
public record CleaningPanelDto(ActiveTaskDto activeTask, List<CleaningRoomDto> rooms) {

    public static CleaningPanelDto withActiveTask(CleaningTask task, Room room, OffsetDateTime lastCleaningDateTime) {
        return new CleaningPanelDto(
                new ActiveTaskDto(
                        task.getId(), task.getStartDateTime(), CleaningRoomDto.of(room, lastCleaningDateTime)),
                List.of());
    }

    /** {@code rooms} ya viene en el orden del panel; {@code lastCleanings} no tiene las habitaciones sin limpiezas. */
    public static CleaningPanelDto withRooms(List<Room> rooms, Map<UUID, OffsetDateTime> lastCleanings) {
        return new CleaningPanelDto(
                null,
                rooms.stream()
                        .map(room -> CleaningRoomDto.of(room, lastCleanings.get(room.getId())))
                        .toList());
    }

    public record ActiveTaskDto(UUID taskId, OffsetDateTime startDateTime, CleaningRoomDto room) {}

    public record CleaningRoomDto(
            UUID roomId,
            String roomNumber,
            RoomCategory categoryRoom,
            RoomStatus status,
            OffsetDateTime lastCleaningDateTime) {

        static CleaningRoomDto of(Room room, OffsetDateTime lastCleaningDateTime) {
            return new CleaningRoomDto(
                    room.getId(), room.getRoomNumber(), room.getCategoryRoom(), room.getStatus(), lastCleaningDateTime);
        }
    }
}

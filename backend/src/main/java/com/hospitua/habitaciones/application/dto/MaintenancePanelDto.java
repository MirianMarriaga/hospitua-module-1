package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/maintenance/panel} (plan-consultar-panel-mantenimiento.md T002): la tarea activa del
 * miembro o todas las habitaciones salvo las {@code Inactive}. Con tarea activa, {@code rooms} llega vacío (FR-001).
 * Los nulos se omiten del JSON ({@code default-property-inclusion: non_null}): sin tarea no viaja {@code activeTask},
 * y una habitación sin mantenimiento no lleva {@code maintenance} ("Sin programar").
 */
public record MaintenancePanelDto(MaintenanceActiveTaskDto activeTask, List<MaintenanceRoomDto> rooms) {

    public MaintenancePanelDto {
        rooms = List.copyOf(rooms);
    }

    public static MaintenancePanelDto withActiveTask(
            UUID taskId, OffsetDateTime startDateTime, MaintenanceRoomDto room) {
        return new MaintenancePanelDto(new MaintenanceActiveTaskDto(taskId, startDateTime, room), List.of());
    }

    /** {@code rooms} ya viene en el orden del panel. */
    public static MaintenancePanelDto withRooms(List<MaintenanceRoomDto> rooms) {
        return new MaintenancePanelDto(null, rooms);
    }

    /**
     * Tarea de reparación abierta del miembro, con su inicio para "Empezaste" y "Tiempo transcurrido" de la vista
     * de tarea activa. Lleva el nombre del panel para que el contrato publique por separado la de limpieza (L41).
     */
    public record MaintenanceActiveTaskDto(UUID taskId, OffsetDateTime startDateTime, MaintenanceRoomDto room) {}

    /**
     * Una fila del panel. {@code overdue}: {@code TechnicalBlock} con el informe {@code Applied} vencido (FR-003);
     * {@code hasOpenTask}: la habitación tiene una tarea de reparación abierta, de cualquier miembro (FR-005).
     */
    public record MaintenanceRoomDto(
            UUID roomId,
            String roomNumber,
            RoomCategory categoryRoom,
            RoomStatus status,
            boolean overdue,
            boolean hasOpenTask,
            MaintenanceRangeDto maintenance) {}

    /** Rango del informe {@code Applied} o, si no hay, del {@code Scheduled} de inicio más temprano (FR-004). */
    public record MaintenanceRangeDto(LocalDate startDate, LocalDate estimatedEndDate) {}
}

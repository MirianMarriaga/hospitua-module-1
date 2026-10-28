package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.MaintenanceNoticeAction;
import com.hospitua.habitaciones.domain.model.Room;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Contenido del aviso de mantenimiento hacia Módulo 2 (T024, "Convenciones de Mensajería"). {@code previousStartDate}
 * y {@code previousEndDate} solo existen en {@code UPDATED}; {@code messageId} y {@code sequenceNumber} los agrega
 * el Outbox (T012).
 */
public record MaintenanceNoticePayload(
        UUID maintenanceId,
        MaintenanceNoticeAction action,
        UUID roomId,
        String categoryRoom,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate previousStartDate,
        LocalDate previousEndDate) {

    public MaintenanceNoticePayload {
        Objects.requireNonNull(maintenanceId, "El maintenanceId no puede ser nulo");
        Objects.requireNonNull(action, "La acción no puede ser nula");
        Objects.requireNonNull(roomId, "La habitación no puede ser nula");
        Objects.requireNonNull(categoryRoom, "La categoría no puede ser nula");
        Objects.requireNonNull(startDate, "La fecha de inicio no puede ser nula");
        Objects.requireNonNull(endDate, "La fecha de fin no puede ser nula");
        if (action == MaintenanceNoticeAction.UPDATED) {
            Objects.requireNonNull(previousStartDate, "La fecha previa de inicio no puede ser nula");
            Objects.requireNonNull(previousEndDate, "La fecha previa de fin no puede ser nula");
        }
    }

    public static MaintenanceNoticePayload scheduled(
            UUID maintenanceId, Room room, LocalDate startDate, LocalDate endDate) {
        return of(MaintenanceNoticeAction.SCHEDULED, maintenanceId, room, startDate, endDate, null, null);
    }

    public static MaintenanceNoticePayload updated(
            UUID maintenanceId,
            Room room,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate previousStartDate,
            LocalDate previousEndDate) {
        return of(
                MaintenanceNoticeAction.UPDATED,
                maintenanceId,
                room,
                startDate,
                endDate,
                previousStartDate,
                previousEndDate);
    }

    public static MaintenanceNoticePayload cancelled(
            UUID maintenanceId, Room room, LocalDate startDate, LocalDate endDate) {
        return of(MaintenanceNoticeAction.CANCELLED, maintenanceId, room, startDate, endDate, null, null);
    }

    private static MaintenanceNoticePayload of(
            MaintenanceNoticeAction action,
            UUID maintenanceId,
            Room room,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate previousStartDate,
            LocalDate previousEndDate) {
        Objects.requireNonNull(room, "La habitación no puede ser nula");
        return new MaintenanceNoticePayload(
                maintenanceId,
                action,
                room.getId(),
                room.getCategoryRoom().name(),
                startDate,
                endDate,
                previousStartDate,
                previousEndDate);
    }
}

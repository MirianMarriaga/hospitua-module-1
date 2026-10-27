package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.MaintenanceNoticeAction;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload plano para los avisos de mantenimiento emitidos hacia Módulo 2 (T024, regla 1).
 * Representa los datos del hecho notificado; messageId y sequenceNumber son agregados por el Outbox.
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

    public static MaintenanceNoticePayload scheduled(
            UUID maintenanceId, UUID roomId, String categoryRoom, LocalDate startDate, LocalDate endDate) {
        return new MaintenanceNoticePayload(
                maintenanceId, MaintenanceNoticeAction.SCHEDULED, roomId, categoryRoom, startDate, endDate, null, null);
    }

    public static MaintenanceNoticePayload updated(
            UUID maintenanceId,
            UUID roomId,
            String categoryRoom,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate previousStartDate,
            LocalDate previousEndDate) {
        return new MaintenanceNoticePayload(
                maintenanceId,
                MaintenanceNoticeAction.UPDATED,
                roomId,
                categoryRoom,
                startDate,
                endDate,
                previousStartDate,
                previousEndDate);
    }

    public static MaintenanceNoticePayload cancelled(
            UUID maintenanceId, UUID roomId, String categoryRoom, LocalDate startDate, LocalDate endDate) {
        return new MaintenanceNoticePayload(
                maintenanceId, MaintenanceNoticeAction.CANCELLED, roomId, categoryRoom, startDate, endDate, null, null);
    }

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("maintenanceId", maintenanceId);
        map.put("action", action.name());
        map.put("roomId", roomId);
        map.put("categoryRoom", categoryRoom);
        map.put("startDate", startDate.toString());
        map.put("endDate", endDate.toString());
        if (previousStartDate != null) {
            map.put("previousStartDate", previousStartDate.toString());
        }
        if (previousEndDate != null) {
            map.put("previousEndDate", previousEndDate.toString());
        }
        return map;
    }
}

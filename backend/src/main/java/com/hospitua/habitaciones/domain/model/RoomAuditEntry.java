package com.hospitua.habitaciones.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada de auditoría para eventos de habitación que no constituyen transiciones de estado.
 */
public record RoomAuditEntry(UUID roomId, RoomAuditEventType eventType, String actorId, Map<String, Object> details) {

    public RoomAuditEntry {
        Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        Objects.requireNonNull(eventType, "El tipo de evento no puede ser nulo");
    }
}

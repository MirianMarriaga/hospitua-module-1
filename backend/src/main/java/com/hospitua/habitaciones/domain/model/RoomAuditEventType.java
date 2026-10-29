package com.hospitua.habitaciones.domain.model;

/**
 * Tipos de eventos auditables para una habitación que no son transiciones de estado.
 */
public enum RoomAuditEventType {
    RESERVATION_STATE_CONFLICT,
    ROOM_EDITED,
    CHECK_IN,
    CHECK_OUT
}

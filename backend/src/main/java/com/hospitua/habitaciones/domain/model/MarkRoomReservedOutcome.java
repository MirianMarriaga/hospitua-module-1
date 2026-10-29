package com.hospitua.habitaciones.domain.model;

/**
 * Resultado de la invocación del caso de uso de marcar habitación como reservada.
 */
public enum MarkRoomReservedOutcome {
    RESERVED,
    ALREADY_RESERVED,
    CONFLICT,
    ROOM_NOT_FOUND
}

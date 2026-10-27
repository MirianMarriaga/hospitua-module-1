package com.hospitua.habitaciones.domain.model;

/**
 * Estados canónicos del ciclo de vida de una habitación física.
 */
public enum RoomStatus {
    Available,
    Reserved,
    Occupied,
    PendingCleaning,
    InCleaning,
    DisabledForRepairs,
    TechnicalBlock,
    Inactive
}

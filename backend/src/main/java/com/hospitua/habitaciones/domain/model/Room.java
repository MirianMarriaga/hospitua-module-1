package com.hospitua.habitaciones.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa una unidad habitacional física.
 */
public class Room {

    private final UUID id;
    private final String roomNumber;
    private final int floor;
    private final RoomCategory categoryRoom;
    private final int maxCapacity;
    private final BigDecimal baseRate;
    private final RoomStatus status;
    private final String reservedByReservationRef;
    private final int version;

    public Room(
            UUID id,
            String roomNumber,
            int floor,
            RoomCategory categoryRoom,
            int maxCapacity,
            BigDecimal baseRate,
            RoomStatus status,
            String reservedByReservationRef,
            int version) {
        this.id = Objects.requireNonNull(id, "El id de la habitación no puede ser nulo");
        this.roomNumber = Objects.requireNonNull(roomNumber, "El número de habitación no puede ser nulo");
        this.floor = floor;
        this.categoryRoom = Objects.requireNonNull(categoryRoom, "La categoría no puede ser nula");
        this.maxCapacity = maxCapacity;
        this.baseRate = Objects.requireNonNull(baseRate, "La tarifa base no puede ser nula");
        this.status = Objects.requireNonNull(status, "El estado no puede ser nulo");
        this.reservedByReservationRef = reservedByReservationRef;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public int getFloor() {
        return floor;
    }

    public RoomCategory getCategoryRoom() {
        return categoryRoom;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public String getReservedByReservationRef() {
        return reservedByReservationRef;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

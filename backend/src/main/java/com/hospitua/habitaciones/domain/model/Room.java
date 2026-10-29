package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.InvalidRoomDataException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa una unidad habitacional física y gobierna
 * su ciclo de vida mediante la máquina de estados canónica de HOSPITUA.
 */
public class Room {

    private final UUID id;
    private final String roomNumber;
    private final int floor;
    private final RoomCategory categoryRoom;
    private final int maxCapacity;
    private final BigDecimal baseRate;
    private RoomStatus status;
    private String reservedByReservationRef;
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

    /**
     * Crea una habitación nueva con un UUID del sistema y estado Available (registrar-habitacion FR-002 a FR-008,
     * FR-011). El número se guarda sin espacios al inicio ni al final; su unicidad la valida el servicio.
     */
    public static Room register(
            String roomNumber, Integer floor, RoomCategory categoryRoom, Integer maxCapacity, BigDecimal baseRate) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new InvalidRoomDataException("roomNumber", "El número de habitación es obligatorio.");
        }
        if (floor == null || floor <= 0) {
            throw new InvalidRoomDataException("floor", "El piso debe ser un número entero mayor a 0.");
        }
        if (categoryRoom == null) {
            throw new InvalidRoomDataException(
                    "categoryRoom", "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique.");
        }
        if (maxCapacity == null || maxCapacity <= 0) {
            throw new InvalidRoomDataException(
                    "maxCapacity", "La capacidad máxima debe ser un número entero mayor a 0.");
        }
        if (baseRate == null
                || baseRate.signum() <= 0
                || baseRate.stripTrailingZeros().scale() > 2) {
            throw new InvalidRoomDataException(
                    "baseRate", "La tarifa base debe ser mayor a 0 y tener máximo dos decimales.");
        }
        return new Room(
                UUID.randomUUID(),
                roomNumber.strip(),
                floor,
                categoryRoom,
                maxCapacity,
                baseRate,
                RoomStatus.Available,
                null,
                0);
    }

    /**
     * Valida y ejecuta la transición hacia el estado objetivo conforme a la máquina de estados canónica.
     * Si el destino es distinto de Reserved, limpia automáticamente reservedByReservationRef.
     */
    public void transitionTo(RoomStatus target) {
        Objects.requireNonNull(target, "El estado destino no puede ser nulo");
        if (!RoomTransitions.allows(this.status, target)) {
            throw new InvalidRoomTransitionException(this.status);
        }
        this.status = target;
        if (target != RoomStatus.Reserved) {
            this.reservedByReservationRef = null;
        }
    }

    /**
     * Transiciona la habitación al estado Reserved y asigna la referencia de reserva.
     * Es el único camino que asigna la referencia de reserva en el dominio.
     */
    public void reserveFor(String reservationRef) {
        if (reservationRef == null || reservationRef.isBlank()) {
            throw new IllegalArgumentException("La referencia de reserva es obligatoria para apartar la habitación.");
        }
        transitionTo(RoomStatus.Reserved);
        this.reservedByReservationRef = reservationRef;
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

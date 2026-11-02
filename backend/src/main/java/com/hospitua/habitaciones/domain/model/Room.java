package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.InvalidRoomDataException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa una unidad habitacional física y gobierna
 * su ciclo de vida mediante la máquina de estados canónica de HOSPITUA.
 */
public class Room {

    private final UUID id;
    private String roomNumber;
    private int floor;
    private RoomCategory categoryRoom;
    private int maxCapacity;
    private BigDecimal baseRate;
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

    public static final int MAX_ROOM_NUMBER_LENGTH = 10;

    /**
     * Forma canónica del número (registrar-habitacion FR-014): sin espacios al inicio ni al final y en mayúsculas, para
     * que "101a" y "101A" sean el mismo número al guardar, al validar la unicidad y al filtrar el inventario.
     */
    public static String normalizeRoomNumber(String roomNumber) {
        return roomNumber.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Crea una habitación nueva con un UUID del sistema y estado Available (registrar-habitacion FR-002 a FR-008,
     * FR-011, FR-014). El número se guarda con {@link #normalizeRoomNumber}; su unicidad la valida el servicio.
     */
    public static Room register(
            String roomNumber, Integer floor, RoomCategory categoryRoom, Integer maxCapacity, BigDecimal baseRate) {
        return new Room(
                UUID.randomUUID(),
                validRoomNumber(roomNumber),
                validFloor(floor),
                validCategory(categoryRoom),
                validMaxCapacity(maxCapacity),
                validBaseRate(baseRate),
                RoomStatus.Available,
                null,
                0);
    }

    /**
     * Editar habitación (plan-editar-habitacion.md T002): aplica los valores del formulario con las mismas reglas del
     * registro (FR-005) y devuelve solo los atributos que cambiaron (FR-006); un valor nulo deja el atributo como está.
     * El piso y la capacidad llegan como decimal para rechazar un valor no entero, y la categoría como texto del
     * catálogo. No cambia el estado: que la habitación esté {@code Available} lo valida el servicio antes.
     *
     * @throws InvalidRoomDataException si un valor no cumple su regla; en ese caso la habitación queda igual
     */
    public List<RoomAttributeChange> applyChanges(
            String newRoomNumber,
            BigDecimal newFloor,
            String newCategoryRoom,
            BigDecimal newMaxCapacity,
            BigDecimal newBaseRate) {
        String number = newRoomNumber == null ? roomNumber : validRoomNumber(newRoomNumber);
        int floorValue = newFloor == null ? floor : validFloor(wholeNumber(newFloor, "floor", FLOOR_RULE));
        RoomCategory category = newCategoryRoom == null ? categoryRoom : categoryFromCatalog(newCategoryRoom);
        int capacity = newMaxCapacity == null
                ? maxCapacity
                : validMaxCapacity(wholeNumber(newMaxCapacity, "maxCapacity", CAPACITY_RULE));
        BigDecimal rate =
                newBaseRate == null ? baseRate : validBaseRate(newBaseRate).setScale(2, RoundingMode.UNNECESSARY);

        List<RoomAttributeChange> changes = new ArrayList<>();
        if (!number.equals(roomNumber)) {
            changes.add(new RoomAttributeChange("roomNumber", roomNumber, number));
        }
        if (floorValue != floor) {
            changes.add(new RoomAttributeChange("floor", floor, floorValue));
        }
        if (category != categoryRoom) {
            changes.add(new RoomAttributeChange("categoryRoom", categoryRoom.name(), category.name()));
        }
        if (capacity != maxCapacity) {
            changes.add(new RoomAttributeChange("maxCapacity", maxCapacity, capacity));
        }
        if (rate.compareTo(baseRate) != 0) {
            changes.add(new RoomAttributeChange("baseRate", baseRate, rate));
        }
        roomNumber = number;
        floor = floorValue;
        categoryRoom = category;
        maxCapacity = capacity;
        baseRate = rate.compareTo(baseRate) == 0 ? baseRate : rate;
        return List.copyOf(changes);
    }

    private static final String ROOM_NUMBER_REQUIRED = "El número de habitación es obligatorio.";
    private static final String FLOOR_RULE = "El piso debe ser un número entero mayor a 0.";
    private static final String CATEGORY_RULE = "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique.";
    private static final String CAPACITY_RULE = "La capacidad máxima debe ser un número entero mayor a 0.";
    private static final String BASE_RATE_RULE = "La tarifa base debe ser mayor a 0 y tener máximo dos decimales.";

    private static String validRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new InvalidRoomDataException("roomNumber", ROOM_NUMBER_REQUIRED);
        }
        String normalizedNumber = normalizeRoomNumber(roomNumber);
        if (normalizedNumber.length() > MAX_ROOM_NUMBER_LENGTH) {
            throw new InvalidRoomDataException(
                    "roomNumber", "El número de habitación debe tener máximo 10 caracteres.");
        }
        return normalizedNumber;
    }

    private static int validFloor(Integer floor) {
        if (floor == null || floor <= 0) {
            throw new InvalidRoomDataException("floor", FLOOR_RULE);
        }
        return floor;
    }

    private static RoomCategory validCategory(RoomCategory categoryRoom) {
        if (categoryRoom == null) {
            throw new InvalidRoomDataException("categoryRoom", CATEGORY_RULE);
        }
        return categoryRoom;
    }

    private static RoomCategory categoryFromCatalog(String categoryRoom) {
        return Arrays.stream(RoomCategory.values())
                .filter(category -> category.name().equals(categoryRoom))
                .findFirst()
                .orElseThrow(() -> new InvalidRoomDataException("categoryRoom", CATEGORY_RULE));
    }

    private static int validMaxCapacity(Integer maxCapacity) {
        if (maxCapacity == null || maxCapacity <= 0) {
            throw new InvalidRoomDataException("maxCapacity", CAPACITY_RULE);
        }
        return maxCapacity;
    }

    /** Mayor a 0, con máximo dos decimales y diez dígitos enteros: lo que cabe en {@code NUMERIC(12,2)}. */
    private static BigDecimal validBaseRate(BigDecimal baseRate) {
        BigDecimal normalized = baseRate == null ? null : baseRate.stripTrailingZeros();
        if (normalized == null
                || normalized.signum() <= 0
                || normalized.scale() > 2
                || normalized.precision() - normalized.scale() > 10) {
            throw new InvalidRoomDataException("baseRate", BASE_RATE_RULE);
        }
        return baseRate;
    }

    /** Un decimal sin parte fraccionaria y dentro del rango de {@code int}; si no, la regla del campo. */
    private static Integer wholeNumber(BigDecimal value, String field, String rule) {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ex) {
            throw new InvalidRoomDataException(field, rule);
        }
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

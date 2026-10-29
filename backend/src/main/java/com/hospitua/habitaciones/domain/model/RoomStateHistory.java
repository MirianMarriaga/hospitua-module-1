package com.hospitua.habitaciones.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa un periodo de una habitación en un estado dentro del historial.
 * Conforme a la regla 8, los periodos son inmutables y solo pueden cerrarse una vez.
 */
public class RoomStateHistory {

    private final UUID id;
    private final UUID roomId;
    private final RoomStatus status;
    private final RoomStatus previousStatus;
    private final OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;
    private final String actorId;
    private final SourceFlow sourceFlow;
    private final String reservationRef;

    public RoomStateHistory(
            UUID id,
            UUID roomId,
            RoomStatus status,
            RoomStatus previousStatus,
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            String actorId,
            SourceFlow sourceFlow,
            String reservationRef) {
        this.id = Objects.requireNonNull(id, "El id del historial no puede ser nulo");
        this.roomId = Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        this.status = Objects.requireNonNull(status, "El estado no puede ser nulo");
        this.previousStatus = previousStatus;
        this.startDateTime = Objects.requireNonNull(startDateTime, "La fecha de inicio no puede ser nula");
        this.endDateTime = endDateTime;
        this.actorId = actorId;
        this.sourceFlow = Objects.requireNonNull(sourceFlow, "El flujo de origen no puede ser nulo");
        this.reservationRef = reservationRef;
    }

    /**
     * Cierra el periodo abierto asignando su fecha de fin.
     * Un periodo solo puede cerrarse una vez.
     */
    public void close(OffsetDateTime endDateTime) {
        if (this.endDateTime != null) {
            throw new IllegalStateException("El periodo del historial ya se encuentra cerrado.");
        }
        this.endDateTime = Objects.requireNonNull(endDateTime, "La fecha de fin no puede ser nula");
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public RoomStatus getPreviousStatus() {
        return previousStatus;
    }

    public OffsetDateTime getStartDateTime() {
        return startDateTime;
    }

    public OffsetDateTime getEndDateTime() {
        return endDateTime;
    }

    public String getActorId() {
        return actorId;
    }

    public SourceFlow getSourceFlow() {
        return sourceFlow;
    }

    public String getReservationRef() {
        return reservationRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomStateHistory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

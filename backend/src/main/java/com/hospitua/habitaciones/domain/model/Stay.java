package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa una estancia física formalizada en Check-In.
 */
public class Stay {

    private final UUID id;
    private final String reservationRef;
    private final UUID roomId;
    private final String source;
    private final LocalDate checkInDate;
    private final LocalDate checkOutDate;
    private final LocalDate expectedCheckinDate;
    private final LocalDate expectedCheckoutDate;
    private final String titularFirstName;
    private final String titularLastName;
    private final String titularDocumentNumber;
    private final int version;

    public Stay(
            UUID id,
            String reservationRef,
            UUID roomId,
            String source,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            LocalDate expectedCheckinDate,
            LocalDate expectedCheckoutDate,
            String titularFirstName,
            String titularLastName,
            String titularDocumentNumber,
            int version) {
        this.id = Objects.requireNonNull(id, "El id de estancia no puede ser nulo");
        this.reservationRef = Objects.requireNonNull(reservationRef, "La referencia de reserva no puede ser nula");
        this.roomId = Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        this.source = Objects.requireNonNull(source, "El origen (source) no puede ser nulo");
        this.checkInDate = Objects.requireNonNull(checkInDate, "La fecha de check-in no puede ser nula");
        this.checkOutDate = checkOutDate;
        this.expectedCheckinDate =
                Objects.requireNonNull(expectedCheckinDate, "La fecha estimada de check-in no puede ser nula");
        this.expectedCheckoutDate =
                Objects.requireNonNull(expectedCheckoutDate, "La fecha estimada de check-out no puede ser nula");
        this.titularFirstName = Objects.requireNonNull(titularFirstName, "El nombre del titular no puede ser nulo");
        this.titularLastName = Objects.requireNonNull(titularLastName, "El apellido del titular no puede ser nulo");
        this.titularDocumentNumber =
                Objects.requireNonNull(titularDocumentNumber, "El documento del titular no puede ser nulo");
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getReservationRef() {
        return reservationRef;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public LocalDate getExpectedCheckinDate() {
        return expectedCheckinDate;
    }

    public LocalDate getExpectedCheckoutDate() {
        return expectedCheckoutDate;
    }

    public String getTitularFirstName() {
        return titularFirstName;
    }

    public String getTitularLastName() {
        return titularLastName;
    }

    public String getTitularDocumentNumber() {
        return titularDocumentNumber;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stay stay)) return false;
        return Objects.equals(id, stay.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

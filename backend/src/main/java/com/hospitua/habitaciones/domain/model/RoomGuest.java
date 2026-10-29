package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad inmutable de dominio que representa a un ocupante físico de una estancia.
 * Conforme a la regla 4, no expone setters ni operaciones de modificación tras el Check-In.
 */
public class RoomGuest {

    private final UUID id;
    private final UUID stayId;
    private final String firstName;
    private final String lastName;
    private final String documentType;
    private final String documentNumber;
    private final String nationality;
    private final LocalDate birthDate;
    private final String originPlace;
    private final String destinationPlace;
    private final boolean isReservationGuest;

    public RoomGuest(
            UUID id,
            UUID stayId,
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            String nationality,
            LocalDate birthDate,
            String originPlace,
            String destinationPlace,
            boolean isReservationGuest) {
        this.id = Objects.requireNonNull(id, "El id de huésped no puede ser nulo");
        this.stayId = Objects.requireNonNull(stayId, "El id de estancia no puede ser nulo");
        this.firstName = Objects.requireNonNull(firstName, "El nombre no puede ser nulo");
        this.lastName = Objects.requireNonNull(lastName, "El apellido no puede ser nulo");
        this.documentType = Objects.requireNonNull(documentType, "El tipo de documento no puede ser nulo");
        this.documentNumber = Objects.requireNonNull(documentNumber, "El número de documento no puede ser nulo");
        this.nationality = Objects.requireNonNull(nationality, "La nacionalidad no puede ser nula");
        this.birthDate = Objects.requireNonNull(birthDate, "La fecha de nacimiento no puede ser nula");
        this.originPlace = originPlace;
        this.destinationPlace = destinationPlace;
        this.isReservationGuest = isReservationGuest;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStayId() {
        return stayId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getNationality() {
        return nationality;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getOriginPlace() {
        return originPlace;
    }

    public String getDestinationPlace() {
        return destinationPlace;
    }

    public boolean isReservationGuest() {
        return isReservationGuest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomGuest that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

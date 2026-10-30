package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.InvalidGuestDataException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * Ocupante real de la habitación en el Check-In, ya validado (plan-registrar-check-in.md T016; FR-006, FR-007, FR-009).
 * {@code originPlace} y {@code destinationPlace} solo existen para extranjeros; para colombianos quedan nulos.
 */
public record CheckInGuest(
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        String nationality,
        LocalDate birthDate,
        String originPlace,
        String destinationPlace,
        boolean reservationGuest) {

    /** Tipos de documento que acepta Módulo 2 (FR-006). */
    public static final Set<String> DOCUMENT_TYPES = Set.of("RC", "TI", "CC", "CE", "PAS", "NIT");

    public static final String COLOMBIA = "Colombia";

    /**
     * Valida y normaliza los datos del huésped {@code guestIndex} (desde 0): textos sin espacios sobrantes y dentro del
     * largo de su columna, tipo de documento admitido, {@code birthDate} anterior a {@code today} y, si no es
     * colombiano, lugar de procedencia y de destino. Lanza {@link InvalidGuestDataException} con el primer campo que
     * no cumple.
     */
    public static CheckInGuest of(
            int guestIndex,
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            String nationality,
            LocalDate birthDate,
            String originPlace,
            String destinationPlace,
            boolean reservationGuest,
            LocalDate today) {
        String first = required(guestIndex, "firstName", firstName, 100, "Los nombres son obligatorios.");
        String last = required(guestIndex, "lastName", lastName, 100, "Los apellidos son obligatorios.");
        String type = required(guestIndex, "documentType", documentType, 10, "El tipo de documento es obligatorio.")
                .toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new InvalidGuestDataException(
                    guestIndex, "documentType", "El tipo de documento debe ser RC, TI, CC, CE, PAS o NIT.");
        }
        String number =
                required(guestIndex, "documentNumber", documentNumber, 50, "El número de documento es obligatorio.");
        String country = required(guestIndex, "nationality", nationality, 100, "La nacionalidad es obligatoria.");
        if (birthDate == null || !birthDate.isBefore(today)) {
            throw new InvalidGuestDataException(
                    guestIndex, "birthDate", "La fecha de nacimiento es obligatoria y debe ser anterior a hoy.");
        }
        boolean foreigner = !COLOMBIA.equalsIgnoreCase(country);
        String origin = foreigner
                ? required(guestIndex, "originPlace", originPlace, 255, "El lugar de procedencia es obligatorio.")
                : null;
        String destination = foreigner
                ? required(guestIndex, "destinationPlace", destinationPlace, 255, "El lugar de destino es obligatorio.")
                : null;
        return new CheckInGuest(
                first,
                last,
                type,
                number,
                foreigner ? country : COLOMBIA,
                birthDate,
                origin,
                destination,
                reservationGuest);
    }

    private static String required(int guestIndex, String field, String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidGuestDataException(guestIndex, field, message);
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new InvalidGuestDataException(
                    guestIndex, field, "El campo admite máximo " + maxLength + " caracteres.");
        }
        return stripped;
    }

    /** Mismo tipo y número de documento, sin distinguir mayúsculas en el número. */
    boolean sameDocumentAs(CheckInGuest other) {
        return documentType.equals(other.documentType) && documentNumber.equalsIgnoreCase(other.documentNumber);
    }
}

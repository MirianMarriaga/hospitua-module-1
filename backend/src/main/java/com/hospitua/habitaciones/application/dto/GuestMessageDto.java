package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomGuest;
import java.time.LocalDate;

/**
 * Huésped de los mensajes planos de Check-In y Check-Out hacia Módulo 2 (plan-registrar-check-in.md y
 * plan-registrar-check-out.md, Contrato 1, campos de {@code guests[]}). Sale del {@link RoomGuest} registrado en el
 * Check-In; los lugares de un huésped colombiano son nulos y no viajan.
 */
public record GuestMessageDto(
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        LocalDate birthDate,
        String nationality,
        String originPlace,
        String destinationPlace) {

    public static GuestMessageDto of(RoomGuest guest) {
        return new GuestMessageDto(
                guest.getFirstName(),
                guest.getLastName(),
                guest.getDocumentType(),
                guest.getDocumentNumber(),
                guest.getBirthDate(),
                guest.getNationality(),
                guest.getOriginPlace(),
                guest.getDestinationPlace());
    }
}

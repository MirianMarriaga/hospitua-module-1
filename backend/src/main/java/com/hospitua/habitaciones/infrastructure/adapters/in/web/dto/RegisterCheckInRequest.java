package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.RegisterCheckInCommand;
import com.hospitua.habitaciones.application.dto.RegisterCheckInCommand.GuestInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de POST /api/check-in (plan-registrar-check-in.md Endpoint 2). Aquí solo se exige la forma; los datos de
 * cada huésped los valida el dominio para responder el campo y la posición del huésped ({@code details.guestIndex}).
 */
public record RegisterCheckInRequest(
        @NotBlank(message = "La referencia de la reserva es obligatoria.")
        @Size(max = 100, message = "La referencia de la reserva admite máximo 100 caracteres.")
        String reservationRef,

        @NotNull(message = "La habitación es obligatoria.") UUID roomId,

        @NotNull(message = "Registra los huéspedes que ocupan la habitación.")
        List<GuestRequest> guests) {

    public RegisterCheckInCommand toCommand(String receptionistId) {
        return new RegisterCheckInCommand(
                reservationRef.strip(),
                roomId,
                guests.stream().map(GuestRequest::toInput).toList(),
                receptionistId);
    }

    /** Un ocupante; {@code isReservationGuest} ausente equivale a {@code false}. */
    public record GuestRequest(
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            String nationality,
            LocalDate birthDate,
            String originPlace,
            String destinationPlace,
            Boolean isReservationGuest) {

        GuestInput toInput() {
            return new GuestInput(
                    firstName,
                    lastName,
                    documentType,
                    documentNumber,
                    nationality,
                    birthDate,
                    originPlace,
                    destinationPlace,
                    Boolean.TRUE.equals(isReservationGuest));
        }
    }
}

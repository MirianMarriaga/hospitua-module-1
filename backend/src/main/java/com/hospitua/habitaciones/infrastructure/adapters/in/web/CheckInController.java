package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.CheckInValidationDto;
import com.hospitua.habitaciones.application.ports.in.ValidateCheckInReservationUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Check-In por habitación desde el Panel de Recepción (plan-registrar-check-in.md T017). */
@Validated
@RestController
@RequestMapping("/api/check-in")
@RequiredArgsConstructor
public class CheckInController {

    private final ValidateCheckInReservationUseCase validateCheckInReservationUseCase;

    /** Paso 1: valida la reserva y la habitación contra la copia local, sin llamar a Módulo 2 (Endpoint 1, FR-003). */
    @GetMapping("/validate")
    public CheckInValidationDto validate(
            @RequestParam
                    @NotBlank(message = "La referencia de la reserva es obligatoria.")
                    @Size(max = 100, message = "La referencia de la reserva admite máximo 100 caracteres.")
                    String reservationRef,
            @RequestParam UUID roomId) {
        return validateCheckInReservationUseCase.validate(reservationRef.strip(), roomId);
    }
}

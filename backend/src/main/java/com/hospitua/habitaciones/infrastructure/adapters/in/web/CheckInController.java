package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.CheckInResultDto;
import com.hospitua.habitaciones.application.dto.CheckInValidationDto;
import com.hospitua.habitaciones.application.ports.in.RegisterCheckInUseCase;
import com.hospitua.habitaciones.application.ports.in.ValidateCheckInReservationUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RegisterCheckInRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final RegisterCheckInUseCase registerCheckInUseCase;
    private final CurrentUserResolver currentUserResolver;

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

    /** Paso 2: registra el Check-In con los ocupantes reales; el recepcionista es el usuario actual (Endpoint 2). */
    @PostMapping
    public ResponseEntity<CheckInResultDto> register(
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody RegisterCheckInRequest request) {
        String receptionistId = currentUserResolver.resolve(xUser);
        CheckInResultDto result = registerCheckInUseCase.registerCheckIn(request.toCommand(receptionistId));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}

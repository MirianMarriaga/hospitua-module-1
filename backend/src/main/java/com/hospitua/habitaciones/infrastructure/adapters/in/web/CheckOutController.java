package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.ActiveStayResponseDto;
import com.hospitua.habitaciones.application.dto.CheckOutResultDto;
import com.hospitua.habitaciones.application.ports.in.GetActiveStayUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterCheckOutUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RegisterCheckOutRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Check-Out por habitación desde el Panel de Recepción (plan-registrar-check-out.md T011). */
@Validated
@RestController
@RequestMapping("/api/check-out")
@RequiredArgsConstructor
public class CheckOutController {

    private final GetActiveStayUseCase getActiveStayUseCase;
    private final RegisterCheckOutUseCase registerCheckOutUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** Paso 1: la estancia activa de la habitación, con datos locales y sin llamar a Módulo 2 (Endpoint 1, FR-003). */
    @GetMapping("/active-stay")
    public ActiveStayResponseDto activeStay(
            @RequestParam
                    @NotBlank(message = "La referencia de la reserva es obligatoria.")
                    @Size(max = 100, message = "La referencia de la reserva admite máximo 100 caracteres.")
                    String reservationRef,
            @RequestParam UUID roomId) {
        return getActiveStayUseCase.getActiveStay(reservationRef.strip(), roomId);
    }

    /** Paso 4: registra la salida y libera la habitación a limpieza; el recepcionista es el usuario actual (Endpoint 2). */
    @PostMapping
    public CheckOutResultDto register(
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody RegisterCheckOutRequest request) {
        String receptionistId = currentUserResolver.resolve(xUser);
        return registerCheckOutUseCase.registerCheckOut(request.toCommand(receptionistId));
    }
}

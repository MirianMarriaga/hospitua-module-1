package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.RegisterCheckOutCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de POST /api/check-out (plan-registrar-check-out.md Endpoint 2). Aquí solo se exige la forma; que
 * {@code confirmed} sea {@code true} lo decide el caso de uso (400 CONFIRMATION_REQUIRED).
 */
public record RegisterCheckOutRequest(
        @NotNull(message = "La estancia es obligatoria.") UUID stayId,

        @NotNull(message = "Indica si confirmas la salida del huésped.")
        Boolean confirmed) {

    public RegisterCheckOutCommand toCommand(String receptionistId) {
        return new RegisterCheckOutCommand(stayId, confirmed, receptionistId);
    }
}

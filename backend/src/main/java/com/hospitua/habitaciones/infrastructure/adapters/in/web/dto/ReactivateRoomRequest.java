package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.ReactivateRoomCommand;
import com.hospitua.habitaciones.domain.exception.UnknownDecommissionException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/rooms/{roomId}/reactivation}: la baja que mostró la confirmación. Llega como texto para
 * responder el mensaje del plan también cuando no tiene formato de UUID.
 */
public record ReactivateRoomRequest(
        @NotNull(message = UnknownDecommissionException.MESSAGE)
        @Pattern(
                regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = UnknownDecommissionException.MESSAGE)
        String decommissionId) {

    public ReactivateRoomCommand toCommand(UUID roomId, String administratorId) {
        return new ReactivateRoomCommand(roomId, UUID.fromString(decommissionId), administratorId);
    }
}

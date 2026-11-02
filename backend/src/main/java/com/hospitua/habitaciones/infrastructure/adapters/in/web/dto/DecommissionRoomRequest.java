package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.DecommissionRoomCommand;
import com.hospitua.habitaciones.domain.exception.InvalidDecommissionReasonException;
import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/rooms/{roomId}/decommission} (FR-008). La categoría llega como texto para responder el
 * mensaje del motivo también cuando no es del catálogo. Que {@code OTHER} exija el detalle lo valida el dominio
 * ({@link RoomDecommission#normalizeDetail}), que también lo recorta.
 */
public record DecommissionRoomRequest(
        @NotNull(message = REASON_REQUIRED)
        @Pattern(regexp = "PERMANENT_REMODELING|FLOOR_CLOSURE|ADMINISTRATIVE_DECISION|OTHER", message = REASON_REQUIRED)
        String reasonCategory,

        @Size(max = RoomDecommission.MAX_DETAIL_LENGTH, message = InvalidDecommissionReasonException.DETAIL_TOO_LONG)
        String reasonDetail) {

    private static final String REASON_REQUIRED = "Selecciona el motivo de la baja.";

    public DecommissionRoomCommand toCommand(UUID roomId, String administratorId) {
        return new DecommissionRoomCommand(
                roomId, DecommissionReason.valueOf(reasonCategory), reasonDetail, administratorId);
    }
}

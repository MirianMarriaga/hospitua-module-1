package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de marcar una habitación como pendiente a limpieza.
 */
public record MarkPendingCleaningResult(UUID roomId, RoomStatus previousStatus, OffsetDateTime transitionDateTime) {

    public MarkPendingCleaningResult {
        Objects.requireNonNull(roomId, "El id de la habitación no puede ser nulo");
        Objects.requireNonNull(previousStatus, "El estado previo no puede ser nulo");
        Objects.requireNonNull(transitionDateTime, "La fecha y hora de transición no puede ser nula");
    }
}

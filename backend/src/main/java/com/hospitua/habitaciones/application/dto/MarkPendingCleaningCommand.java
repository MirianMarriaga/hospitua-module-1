package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para marcar una habitación como pendiente a limpieza.
 */
public record MarkPendingCleaningCommand(UUID roomId, SourceFlow sourceFlow, String actorId) {

    public MarkPendingCleaningCommand {
        Objects.requireNonNull(roomId, "El id de la habitación no puede ser nulo");
        Objects.requireNonNull(sourceFlow, "El flujo de origen no puede ser nulo");
    }
}

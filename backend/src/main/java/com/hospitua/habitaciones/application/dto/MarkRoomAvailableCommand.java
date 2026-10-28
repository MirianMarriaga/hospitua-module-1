package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.util.UUID;

/**
 * Comando para marcar una habitación como disponible (T018, plan-marcar-habitacion-como-disponible.md).
 */
public record MarkRoomAvailableCommand(UUID roomId, SourceFlow sourceFlow, String actorId, String reservationRef) {}

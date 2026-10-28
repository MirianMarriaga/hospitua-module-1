package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado de la transición hacia Available (T018, plan-marcar-habitacion-como-disponible.md).
 */
public record MarkRoomAvailableResult(UUID roomId, RoomStatus previousStatus, OffsetDateTime transitionDateTime) {}

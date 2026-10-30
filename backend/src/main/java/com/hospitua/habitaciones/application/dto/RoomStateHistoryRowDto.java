package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un periodo del historial de estados (consultar-historial FR-003, FR-004). {@code categoryRoom} es el tipo actual de
 * la habitación; {@code endDateTime} es nulo si el periodo sigue abierto y {@code actorName}, si la transición fue
 * autónoma.
 */
public record RoomStateHistoryRowDto(
        UUID roomId,
        String roomNumber,
        RoomCategory categoryRoom,
        RoomStatus status,
        OffsetDateTime startDateTime,
        OffsetDateTime endDateTime,
        String actorName,
        SourceFlow sourceFlow,
        String sourceFlowName) {

    /** Toma el nombre legible del flujo de {@link SourceFlow#displayName()}. */
    public RoomStateHistoryRowDto(
            UUID roomId,
            String roomNumber,
            RoomCategory categoryRoom,
            RoomStatus status,
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            String actorName,
            SourceFlow sourceFlow) {
        this(
                roomId,
                roomNumber,
                categoryRoom,
                status,
                startDateTime,
                endDateTime,
                actorName,
                sourceFlow,
                sourceFlow.displayName());
    }
}

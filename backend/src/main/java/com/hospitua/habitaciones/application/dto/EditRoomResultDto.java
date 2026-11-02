package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomAttributeChange;
import java.util.List;

/**
 * Respuesta de {@code PATCH /api/rooms/{roomId}}: los atributos que cambiaron ("Antes / Ahora") y los datos actuales de
 * la habitación. Con {@code changed = false} no se guardó ni se auditó nada (FR-007, FR-010).
 */
public record EditRoomResultDto(boolean changed, List<RoomAttributeChange> changes, RoomDto room) {

    public EditRoomResultDto {
        changes = List.copyOf(changes);
    }
}

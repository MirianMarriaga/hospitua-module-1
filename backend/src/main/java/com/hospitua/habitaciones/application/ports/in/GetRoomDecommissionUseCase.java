package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.RoomDecommissionInfoDto;
import java.util.UUID;

/** Datos de la baja vigente de una habitación {@code Inactive} (plan-marcar-habitacion-como-disponible.md, FR-009). */
public interface GetRoomDecommissionUseCase {

    RoomDecommissionInfoDto getCurrent(UUID roomId);
}

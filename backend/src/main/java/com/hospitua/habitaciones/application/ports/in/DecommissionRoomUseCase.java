package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.DecommissionRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDecommissionDto;

/** Dar de baja una habitación {@code Available} sin reservas vigentes en Módulo 2 (plan-dar-de-baja-habitacion.md). */
public interface DecommissionRoomUseCase {

    RoomDecommissionDto decommission(DecommissionRoomCommand command);
}

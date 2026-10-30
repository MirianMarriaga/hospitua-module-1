package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDto;

/**
 * Puerto de entrada para registrar una habitación nueva en estado Available (plan-registrar-habitacion.md).
 */
public interface RegisterRoomUseCase {

    RoomDto register(RegisterRoomCommand command);
}

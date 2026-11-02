package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.EditRoomCommand;
import com.hospitua.habitaciones.application.dto.EditRoomResultDto;

/** Editar los atributos de una habitación {@code Available} (plan-editar-habitacion.md). */
public interface EditRoomUseCase {

    EditRoomResultDto edit(EditRoomCommand command);
}

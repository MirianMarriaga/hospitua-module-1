package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedResult;

/**
 * Puerto de entrada para marcar reactivamente una habitación como reservada ante un evento de reserva de Módulo 2
 * o al confirmar el fin de limpieza.
 */
public interface MarkRoomReservedUseCase {

    MarkRoomReservedResult markReserved(MarkRoomReservedCommand command);
}

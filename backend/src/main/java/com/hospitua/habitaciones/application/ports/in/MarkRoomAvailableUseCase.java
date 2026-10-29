package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableResult;

/**
 * Puerto transversal para marcar una habitación como disponible (T018, plan-marcar-habitacion-como-disponible.md).
 */
public interface MarkRoomAvailableUseCase {

    /**
     * Marca una habitación como Available validando el origen según el flujo invocador.
     * Debe ejecutarse dentro de una transacción ya abierta por el flujo invocador (MANDATORY).
     *
     * @param command comando con el roomId, sourceFlow, actorId y reservationRef
     * @return resultado con el roomId, previousStatus y la marca de tiempo de la transición
     */
    MarkRoomAvailableResult markAvailable(MarkRoomAvailableCommand command);
}

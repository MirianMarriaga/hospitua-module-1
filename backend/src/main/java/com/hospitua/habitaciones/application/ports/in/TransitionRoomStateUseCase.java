package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Puerto de entrada primario para la validación y ejecución de transiciones de estado de habitación.
 */
public interface TransitionRoomStateUseCase {

    /**
     * Ejecuta la transición de una habitación existente hacia el estado objetivo, actualizando su estado
     * y registrando el periodo correspondiente en el historial de estados dentro de la misma transacción.
     *
     * @return marca de tiempo del periodo abierto generado por el reloj del sistema
     */
    OffsetDateTime transition(
            UUID roomId, RoomStatus target, String actorId, SourceFlow sourceFlow, String reservationRef);

    /**
     * Registra el periodo inicial abierto para una habitación nueva con previous_status nulo.
     *
     * @return marca de tiempo del periodo inicial abierto generado por el reloj del sistema
     */
    OffsetDateTime registerInitialStatus(UUID roomId, String actorId, SourceFlow sourceFlow);
}

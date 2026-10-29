package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.out.RoomStateHistoryRepositoryPort;
import com.hospitua.habitaciones.domain.model.RoomStateHistory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado de cerrar el periodo anterior y abrir el nuevo periodo en el historial de estados.
 * Invocado exclusivamente por el servicio de transición de estados conforme a la regla 8.
 */
@Service
@RequiredArgsConstructor
public class RoomStateHistoryRecorder {

    private final RoomStateHistoryRepositoryPort historyRepositoryPort;
    private final Clock clock;

    /**
     * Cierra el periodo abierto (si existe) y abre un nuevo periodo en el historial.
     * Devuelve la marca de tiempo generada por el servidor a partir del Clock.
     */
    public OffsetDateTime recordTransition(
            UUID roomId,
            RoomStatus newStatus,
            RoomStatus previousStatus,
            String actorId,
            SourceFlow sourceFlow,
            String reservationRef) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        historyRepositoryPort.findOpenByRoomId(roomId).ifPresent(openPeriod -> {
            openPeriod.close(now);
            historyRepositoryPort.save(openPeriod);
        });

        RoomStateHistory newPeriod = new RoomStateHistory(
                UUID.randomUUID(), roomId, newStatus, previousStatus, now, null, actorId, sourceFlow, reservationRef);
        historyRepositoryPort.save(newPeriod);

        return now;
    }
}

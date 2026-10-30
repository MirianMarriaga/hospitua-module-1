package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.CheckRoomReservationConflictsUseCase;
import com.hospitua.habitaciones.application.ports.out.Module2ReservationConflictClientPort;
import com.hospitua.habitaciones.domain.model.ReservationConflictQuery;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Arma el rango de cada flujo y consulta a Módulo 2 (consultar-reservas T027). Sin {@code @Transactional}: la llamada
 * REST va fuera de la transacción del invocador, que después revalida con la fila bloqueada.
 */
@Service
@RequiredArgsConstructor
public class RoomReservationConflictService implements CheckRoomReservationConflictsUseCase {

    /** Hoy + 365 días: 366 días contando hoy, el rango máximo de la consulta de Módulo 2 (FR-023). */
    public static final int DECOMMISSION_HORIZON_DAYS = 365;

    private final Module2ReservationConflictClientPort module2Client;
    private final Clock clock;

    @Override
    public ReservationConflictResult checkMaintenanceConflict(UUID roomId, LocalDate startDate, LocalDate endDate) {
        return module2Client.checkConflicts(new ReservationConflictQuery(roomId, startDate, endDate));
    }

    @Override
    public ReservationConflictResult checkDecommissionConflict(UUID roomId) {
        LocalDate today = LocalDate.now(clock);
        return module2Client.checkConflicts(
                new ReservationConflictQuery(roomId, today, today.plusDays(DECOMMISSION_HORIZON_DAYS)));
    }
}

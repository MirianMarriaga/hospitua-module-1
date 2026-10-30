package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.GetReceptionPanelUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.ReceptionDashboardKpis;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los cuatro indicadores del turno desde la copia local, las estancias y el inventario
 * (plan-consultar-panel-recepcion.md T022; FR-002, FR-011). Las llegadas cuentan las mismas filas que muestra la
 * pestaña Llegadas. {@code REPEATABLE_READ} calcula los cuatro sobre una misma instantánea.
 */
@Service
@RequiredArgsConstructor
public class ReceptionPanelQueryService implements GetReceptionPanelUseCase {

    private final DailyReservationRepositoryPort reservationRepository;
    private final StayRepositoryPort stayRepository;
    private final RoomRepositoryPort roomRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReceptionDashboardKpis getKpis() {
        LocalDate today = LocalDate.now(clock);
        Long arrivalsToday = reservationRepository.isDailyListReceived(today)
                ? reservationRepository.countPendingArrivalRooms(today)
                : null;
        return new ReceptionDashboardKpis(
                arrivalsToday,
                stayRepository.countActiveWithExpectedCheckoutOn(today),
                stayRepository.countActiveWithExpectedCheckoutBefore(today),
                roomRepository.countByStatus(RoomStatus.Occupied));
    }
}

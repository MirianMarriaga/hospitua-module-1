package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.ReceptionDashboardKpis;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceptionPanelQueryServiceTest {

    // 23:30 del 9-10 en Bogotá: en UTC ya es 10-10, así que "hoy" debe salir del reloj de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Mock
    private DailyReservationRepositoryPort reservationRepository;

    @Mock
    private StayRepositoryPort stayRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    private ReceptionPanelQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReceptionPanelQueryService(reservationRepository, stayRepository, roomRepository, CLOCK);
        when(stayRepository.countActiveWithExpectedCheckoutOn(TODAY)).thenReturn(8L);
        when(stayRepository.countActiveWithExpectedCheckoutBefore(TODAY)).thenReturn(1L);
        when(roomRepository.countByStatus(RoomStatus.Occupied)).thenReturn(34L);
    }

    @Test
    @DisplayName("T020: los cuatro KPIs salen de la copia local, las estancias y el inventario, con hoy de Bogotá")
    void computesTheFourKpis() {
        when(reservationRepository.isDailyListReceived(TODAY)).thenReturn(true);
        when(reservationRepository.countPendingArrivalRooms(TODAY)).thenReturn(12);

        assertThat(service.getKpis()).isEqualTo(new ReceptionDashboardKpis(12, 8, 1, 34));
    }

    @Test
    @DisplayName("T020: sin la lista de las 00:00 de hoy, arrivalsToday es nulo y los demás KPIs siguen")
    void withoutTodaysList_arrivalsIsNull() {
        when(reservationRepository.isDailyListReceived(TODAY)).thenReturn(false);

        assertThat(service.getKpis()).isEqualTo(new ReceptionDashboardKpis(null, 8, 1, 34));
        verify(reservationRepository, never()).countPendingArrivalRooms(any());
    }
}

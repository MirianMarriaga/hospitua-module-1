package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.out.Module2ReservationConflictClientPort;
import com.hospitua.habitaciones.domain.exception.ExternalModule2UnavailableException;
import com.hospitua.habitaciones.domain.model.ReservationConflictQuery;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class RoomReservationConflictServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    // 23:30 del 31-12 en Bogotá: en UTC ya es 1-1, así que "hoy" debe salir del reloj de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-01T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);

    @Mock
    private Module2ReservationConflictClientPort module2Client;

    private RoomReservationConflictService service;

    @BeforeEach
    void setUp() {
        service = new RoomReservationConflictService(module2Client, CLOCK);
    }

    @Test
    @DisplayName("T022: la baja consulta desde hoy (Bogotá) hasta hoy + 365 días")
    void decommission_queriesFromTodayToOneYearAhead() {
        when(module2Client.checkConflicts(any())).thenReturn(new ReservationConflictResult(List.of()));

        service.checkDecommissionConflict(ROOM_ID);

        verify(module2Client).checkConflicts(new ReservationConflictQuery(ROOM_ID, TODAY, LocalDate.of(2027, 12, 31)));
    }

    @Test
    @DisplayName("T022: el bloqueo técnico consulta del inicio al fin estimado, tal como llegan")
    void maintenance_queriesTheGivenRange() {
        LocalDate start = LocalDate.of(2027, 1, 10);
        when(module2Client.checkConflicts(any())).thenReturn(new ReservationConflictResult(List.of()));

        service.checkMaintenanceConflict(ROOM_ID, start, start.plusDays(4));

        verify(module2Client).checkConflicts(new ReservationConflictQuery(ROOM_ID, start, start.plusDays(4)));
    }

    @Test
    @DisplayName("T022: un fin anterior al inicio se rechaza sin consultar a Módulo 2")
    void maintenance_invertedRange_doesNotCallModule2() {
        LocalDate start = LocalDate.of(2027, 1, 10);

        assertThatThrownBy(() -> service.checkMaintenanceConflict(ROOM_ID, start, start.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(module2Client);
    }

    @Test
    @DisplayName("T022: items vacío no es conflicto; con reservas, hasConflict y cada reserva devuelta")
    void mapsEmptyAndNonEmptyResults() {
        LocalDate start = LocalDate.of(2027, 1, 10);
        ConflictingReservation reservation =
                new ConflictingReservation("RSV-3F9A1C7B", "ACTIVE", start.plusDays(1), start.plusDays(4));
        when(module2Client.checkConflicts(new ReservationConflictQuery(ROOM_ID, start, start)))
                .thenReturn(new ReservationConflictResult(List.of()));
        when(module2Client.checkConflicts(new ReservationConflictQuery(ROOM_ID, start, start.plusDays(2))))
                .thenReturn(new ReservationConflictResult(List.of(reservation)));

        assertThat(service.checkMaintenanceConflict(ROOM_ID, start, start).hasConflict())
                .isFalse();
        ReservationConflictResult conflict = service.checkMaintenanceConflict(ROOM_ID, start, start.plusDays(2));
        assertThat(conflict.hasConflict()).isTrue();
        assertThat(conflict.reservations()).containsExactly(reservation);
    }

    @Test
    @DisplayName("T022: si Módulo 2 no responde, propaga ExternalModule2UnavailableException")
    void propagatesModule2Unavailable() {
        when(module2Client.checkConflicts(any()))
                .thenThrow(new ExternalModule2UnavailableException(new ResourceAccessException("timeout")));

        assertThatThrownBy(() -> service.checkDecommissionConflict(ROOM_ID))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
    }
}

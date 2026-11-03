package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto;
import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto.ConflictDto;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidDateRangeException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceAvailabilityServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private TechnicalBlockReportRepositoryPort reportRepository;

    private MaintenanceAvailabilityService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(BOGOTA).toInstant(), BOGOTA);
        service = new MaintenanceAvailabilityService(roomRepository, reportRepository, clock);
    }

    @Test
    @DisplayName("T004: sin mantenimientos que se crucen → available = true y conflicts vacío")
    void noOverlap_available() {
        givenRoom(RoomStatus.Available);
        when(reportRepository.findActiveOverlapping(ROOM_ID, day(2), day(5))).thenReturn(List.of());

        assertThat(service.consult(ROOM_ID, day(2), day(5)))
                .isEqualTo(new MaintenanceAvailabilityDto(ROOM_ID, true, List.of()));
    }

    @Test
    @DisplayName(
            "T005: un Scheduled del 24 al 28 se cruza con la estadía del 22 al 26; dos cruces, ordenados por inicio")
    void overlaps_returnedInOrder() {
        givenRoom(RoomStatus.Available);
        when(reportRepository.findActiveOverlapping(ROOM_ID, day(2), day(6)))
                .thenReturn(List.of(report(4, 8), report(6, 9)));

        MaintenanceAvailabilityDto result = service.consult(ROOM_ID, day(2), day(6));

        assertThat(result.available()).isFalse();
        assertThat(result.conflicts())
                .containsExactly(new ConflictDto(day(4), day(8)), new ConflictDto(day(6), day(9)));
    }

    @Test
    @DisplayName("T006: DisabledForRepairs no bloquea fechas futuras, pero un Scheduled de esa habitación sí")
    void disabledForRepairs_futureDates() {
        givenRoom(RoomStatus.DisabledForRepairs);
        when(reportRepository.findActiveOverlapping(ROOM_ID, day(1), day(3))).thenReturn(List.of());
        assertThat(service.consult(ROOM_ID, day(1), day(3)).available()).isTrue();

        when(reportRepository.findActiveOverlapping(ROOM_ID, day(5), day(7))).thenReturn(List.of(report(6, 6)));
        assertThat(service.consult(ROOM_ID, day(5), day(7)).conflicts())
                .containsExactly(new ConflictDto(day(6), day(6)));
    }

    @Test
    @DisplayName("T006: un Applied vencido no bloquea fechas futuras (la consulta empieza hoy y él terminó antes)")
    void overdueApplied_doesNotBlockFuture() {
        givenRoom(RoomStatus.TechnicalBlock);
        when(reportRepository.findActiveOverlapping(ROOM_ID, day(1), day(3))).thenReturn(List.of());

        assertThat(service.consult(ROOM_ID, day(1), day(3)).available()).isTrue();
        verify(reportRepository, never()).findAppliedByRoomIds(any());
    }

    @Test
    @DisplayName("FR-014: hoy en DisabledForRepairs → cruce de un día en la fecha actual, antes de los demás")
    void disabledForRepairs_today() {
        givenRoom(RoomStatus.DisabledForRepairs);
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, day(4))).thenReturn(List.of(report(3, 5)));

        assertThat(service.consult(ROOM_ID, TODAY, day(4)).conflicts())
                .containsExactly(new ConflictDto(TODAY, TODAY), new ConflictDto(day(3), day(5)));
    }

    @Test
    @DisplayName("FR-014: TechnicalBlock con el informe vencido → cruce de hoy; con el vigente, solo su rango")
    void technicalBlock_today() {
        givenRoom(RoomStatus.TechnicalBlock);
        TechnicalBlockReport overdue = report(-5, -1);
        overdue.apply();
        when(reportRepository.findAppliedByRoomIds(List.of(ROOM_ID))).thenReturn(Map.of(ROOM_ID, overdue));
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, day(1))).thenReturn(List.of());

        assertThat(service.consult(ROOM_ID, TODAY, day(1)).conflicts()).containsExactly(new ConflictDto(TODAY, TODAY));

        TechnicalBlockReport current = report(-1, 2);
        current.apply();
        when(reportRepository.findAppliedByRoomIds(List.of(ROOM_ID))).thenReturn(Map.of(ROOM_ID, current));
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, day(1))).thenReturn(List.of(current));

        assertThat(service.consult(ROOM_ID, TODAY, day(1)).conflicts())
                .containsExactly(new ConflictDto(day(-1), day(2)));
    }

    @Test
    @DisplayName("FR-014: TechnicalBlock sin informe Applied (datos inconsistentes) también bloquea hoy")
    void technicalBlockWithoutApplied_blocksToday() {
        givenRoom(RoomStatus.TechnicalBlock);
        when(reportRepository.findAppliedByRoomIds(List.of(ROOM_ID))).thenReturn(Map.of());
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, TODAY)).thenReturn(List.of());

        assertThat(service.consult(ROOM_ID, TODAY, TODAY).available()).isFalse();
    }

    @Test
    @DisplayName("Un rango que empieza antes de hoy se consulta desde hoy")
    void rangeStartingBeforeToday_clipped() {
        givenRoom(RoomStatus.Available);
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, day(2))).thenReturn(List.of());

        assertThat(service.consult(ROOM_ID, day(-3), day(2)).available()).isTrue();
    }

    @Test
    @DisplayName("T008: las validaciones fallidas no consultan la habitación ni los mantenimientos")
    void invalidRange_noQueries() {
        assertThatThrownBy(() -> service.consult(ROOM_ID, day(3), day(2)))
                .isInstanceOf(InvalidDateRangeException.class);
        assertThatThrownBy(() -> service.consult(ROOM_ID, day(-5), day(-1)))
                .isInstanceOf(InvalidDateRangeException.class);

        verifyNoInteractions(roomRepository, reportRepository);
    }

    @Test
    @DisplayName("T009: roomId inexistente → ROOM_NOT_FOUND sin evaluar mantenimientos")
    void unknownRoom() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consult(ROOM_ID, day(1), day(2))).isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(reportRepository);
    }

    private void givenRoom(RoomStatus status) {
        when(roomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(
                        new Room(ROOM_ID, "106", 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), status, null, 0)));
    }

    private static TechnicalBlockReport report(int fromDays, int toDays) {
        return TechnicalBlockReport.schedule(
                ROOM_ID,
                "mtorres",
                "Cambio preventivo del aire acondicionado.",
                day(fromDays),
                day(toDays),
                OffsetDateTime.parse("2026-10-08T15:00:00-05:00"));
    }

    private static LocalDate day(int offset) {
        return TODAY.plusDays(offset);
    }
}

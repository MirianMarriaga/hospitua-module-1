package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto;
import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto.ConflictDto;
import com.hospitua.habitaciones.application.dto.ScheduleTechnicalBlockCommand;
import com.hospitua.habitaciones.application.dto.TechnicalBlockCreatedDto;
import com.hospitua.habitaciones.application.ports.in.CheckRoomReservationConflictsUseCase;
import com.hospitua.habitaciones.application.ports.in.ConsultMaintenanceAvailabilityUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidDateRangeException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.InvalidTechnicalBlockReasonException;
import com.hospitua.habitaciones.domain.exception.MaintenanceConflictException;
import com.hospitua.habitaciones.domain.exception.ReservationConflictException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.MaintenanceLimits;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ScheduleTechnicalBlockService} con su {@link TechnicalBlockRegistration} real y los puertos simulados
 * (plan-programar-bloqueo-tecnico-para-habitacion.md T006 a T012).
 */
@ExtendWith(MockitoExtension.class)
class ScheduleTechnicalBlockServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T15:00:00-05:00");
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String MEMBER = "mtorres";
    private static final String REASON = "Cambio preventivo del sistema de aire acondicionado.";
    private static final ReservationConflictResult NO_RESERVATIONS = new ReservationConflictResult(List.of());

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private CheckRoomReservationConflictsUseCase reservationConflicts;

    @Mock
    private ConsultMaintenanceAvailabilityUseCase maintenanceAvailability;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Mock
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Mock
    private MaintenanceNoticePublisher maintenanceNoticePublisher;

    private ScheduleTechnicalBlockService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), BOGOTA);
        TechnicalBlockRegistration registration = new TechnicalBlockRegistration(
                roomRepository,
                maintenanceAvailability,
                transitionRoomStateUseCase,
                reportRepository,
                maintenanceNoticePublisher,
                clock);
        service = new ScheduleTechnicalBlockService(
                roomRepository, reservationConflicts, registration, new MaintenanceLimits(90, 365), clock);
    }

    @Test
    @DisplayName("T006: inicio hoy aplica en la misma transacción (Applied, TechnicalBlock con el miembro) y publica"
            + " un solo aviso SCHEDULED con el inicio y el fin estimado")
    void startsToday_appliesAndPublishes() {
        Room room = givenRoom(RoomStatus.Available);
        givenNoConflicts(TODAY, TODAY.plusDays(2));

        TechnicalBlockCreatedDto created = service.schedule(command("  " + REASON + "  ", TODAY, TODAY.plusDays(2)));

        assertThat(created.status()).isEqualTo(TechnicalBlockStatus.Applied);
        TechnicalBlockReport saved = savedReport();
        assertThat(saved.getId()).isEqualTo(created.technicalBlockId());
        assertThat(saved.getStatus()).isEqualTo(TechnicalBlockStatus.Applied);
        assertThat(saved.getReason()).isEqualTo(REASON);
        assertThat(saved.getMaintenanceStaffMemberId()).isEqualTo(MEMBER);
        assertThat(saved.getReportDateTime()).isEqualTo(NOW);
        InOrder order = inOrder(
                roomRepository,
                maintenanceAvailability,
                transitionRoomStateUseCase,
                reportRepository,
                maintenanceNoticePublisher);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(maintenanceAvailability).consult(ROOM_ID, TODAY, TODAY.plusDays(2));
        order.verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.TechnicalBlock, MEMBER, SourceFlow.TECHNICAL_BLOCK, null);
        order.verify(reportRepository).save(any());
        order.verify(maintenanceNoticePublisher)
                .publishScheduled(created.technicalBlockId(), room, TODAY, TODAY.plusDays(2));
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"Available", "Occupied", "Reserved", "DisabledForRepairs"})
    @DisplayName("T007: inicio futuro registra Scheduled sin cambiar la habitación, desde cualquier estado menos"
            + " Inactive, y publica el aviso con el id del informe y el rango programado")
    void futureStart_registersScheduled(RoomStatus status) {
        Room room = givenRoom(status);
        LocalDate start = TODAY.plusDays(12);
        givenNoConflicts(start, start.plusDays(2));

        TechnicalBlockCreatedDto created = service.schedule(command(REASON, start, start.plusDays(2)));

        assertThat(created.status()).isEqualTo(TechnicalBlockStatus.Scheduled);
        assertThat(savedReport().getStatus()).isEqualTo(TechnicalBlockStatus.Scheduled);
        verifyNoInteractions(transitionRoomStateUseCase);
        verify(maintenanceNoticePublisher).publishScheduled(created.technicalBlockId(), room, start, start.plusDays(2));
    }

    @Test
    @DisplayName("T008: una reserva en el rango → RESERVATION_CONFLICT con sus datos y sin abrir la transacción")
    void reservationConflict() {
        givenRoom(RoomStatus.Available);
        LocalDate start = TODAY.plusDays(10);
        when(reservationConflicts.checkMaintenanceConflict(ROOM_ID, start, start.plusDays(3)))
                .thenReturn(new ReservationConflictResult(List.of(
                        new ConflictingReservation("RES-10502", "ACTIVE", start.plusDays(1), start.plusDays(4)))));

        assertThatThrownBy(() -> service.schedule(command(REASON, start, start.plusDays(3))))
                .isInstanceOfSatisfying(ReservationConflictException.class, ex -> {
                    assertThat(ex.getMessage())
                            .isEqualTo("La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras"
                                    + " fechas.");
                    assertThat(ex.details()).containsKey("reservations");
                });
        verify(roomRepository, never()).lockById(any());
        verifyNoInteractions(maintenanceAvailability, reportRepository, maintenanceNoticePublisher);
    }

    @Test
    @DisplayName("T009: si la consulta de mantenimientos responde no disponible → MAINTENANCE_CONFLICT con los cruces")
    void maintenanceConflict() {
        givenRoom(RoomStatus.Available);
        LocalDate start = TODAY.plusDays(10);
        when(reservationConflicts.checkMaintenanceConflict(ROOM_ID, start, start.plusDays(3)))
                .thenReturn(NO_RESERVATIONS);
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room(RoomStatus.Available)));
        when(maintenanceAvailability.consult(ROOM_ID, start, start.plusDays(3)))
                .thenReturn(new MaintenanceAvailabilityDto(
                        ROOM_ID, false, List.of(new ConflictDto(start.plusDays(2), start.plusDays(5)))));

        assertThatThrownBy(() -> service.schedule(command(REASON, start, start.plusDays(3))))
                .isInstanceOfSatisfying(MaintenanceConflictException.class, ex -> {
                    assertThat(ex.getMessage())
                            .isEqualTo("Ya hay un mantenimiento programado o en curso del 20-10-2026 al 23-10-2026."
                                    + " Elige otras fechas.");
                    assertThat(ex.details())
                            .isEqualTo(Map.of(
                                    "conflicts",
                                    List.of(Map.of("maintenanceStart", "2026-10-20", "maintenanceEnd", "2026-10-23"))));
                });
        verifyNoInteractions(transitionRoomStateUseCase, reportRepository, maintenanceNoticePublisher);
    }

    @Test
    @DisplayName("T010: justificación vacía, solo espacios o de más de 500 caracteres se rechaza sin consultar;"
            + " 500 caracteres tras recortar es válida")
    void invalidReason_noQueries() {
        for (String reason : new String[] {null, "", "   ", "a".repeat(501)}) {
            assertThatThrownBy(() -> service.schedule(command(reason, TODAY.plusDays(1), TODAY.plusDays(2))))
                    .isInstanceOfSatisfying(
                            InvalidTechnicalBlockReasonException.class,
                            ex -> assertThat(ex.details()).containsEntry("field", "reason"));
        }
        verifyNoInteractions(roomRepository, reservationConflicts, maintenanceNoticePublisher);
        assertThat(TechnicalBlockReport.normalizeReason("  " + "a".repeat(500) + " "))
                .hasSize(500);
    }

    @Test
    @DisplayName("T011: inicio pasado, fin anterior, rango > 90 días o antelación > 365 se rechazan sin consultar;"
            + " fin igual al inicio es válido")
    void invalidDates_noQueries() {
        assertRule(TODAY.minusDays(1), TODAY.plusDays(1), "START_IN_PAST");
        assertRule(TODAY.plusDays(5), TODAY.plusDays(4), "END_BEFORE_START");
        assertRule(TODAY.plusDays(1), TODAY.plusDays(92), "RANGE_TOO_LONG");
        assertRule(TODAY.plusDays(366), TODAY.plusDays(367), "START_TOO_FAR");
        verifyNoInteractions(roomRepository, reservationConflicts, maintenanceAvailability, reportRepository);

        givenRoom(RoomStatus.Available);
        givenNoConflicts(TODAY.plusDays(3), TODAY.plusDays(3));
        assertThat(service.schedule(command(REASON, TODAY.plusDays(3), TODAY.plusDays(3)))
                        .status())
                .isEqualTo(TechnicalBlockStatus.Scheduled);
    }

    @Test
    @DisplayName("T012: Inactive siempre, o inicio hoy fuera de Available → ROOM_INVALID_STATE sin consultar")
    void invalidState_noQueries() {
        givenRoomForValidation(RoomStatus.Inactive);
        assertThatThrownBy(() -> service.schedule(command(REASON, TODAY.plusDays(5), TODAY.plusDays(6))))
                .isInstanceOfSatisfying(
                        InvalidRoomTransitionException.class,
                        ex -> assertThat(ex.getMessage())
                                .isEqualTo("La habitación está dada de baja; no se puede programar mantenimiento."));

        givenRoomForValidation(RoomStatus.Occupied);
        assertThatThrownBy(() -> service.schedule(command(REASON, TODAY, TODAY.plusDays(1))))
                .isInstanceOfSatisfying(InvalidRoomTransitionException.class, ex -> {
                    assertThat(ex.getMessage())
                            .isEqualTo("Hoy la habitación está Ocupada. Para empezar hoy debe estar disponible; elige"
                                    + " una fecha de inicio posterior.");
                    assertThat(ex.details()).containsEntry("currentStatus", "Occupied");
                });
        verifyNoInteractions(reservationConflicts, maintenanceAvailability, reportRepository);
    }

    @Test
    @DisplayName("T012: si el estado cambia mientras Módulo 2 responde, la revalidación con la fila bloqueada lo"
            + " rechaza sin registrar")
    void stateChangedBeforeLock() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room(RoomStatus.Available)));
        when(reservationConflicts.checkMaintenanceConflict(ROOM_ID, TODAY, TODAY.plusDays(1)))
                .thenReturn(NO_RESERVATIONS);
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room(RoomStatus.InCleaning)));

        assertThatThrownBy(() -> service.schedule(command(REASON, TODAY, TODAY.plusDays(1))))
                .isInstanceOf(InvalidRoomTransitionException.class);
        verifyNoInteractions(maintenanceAvailability, transitionRoomStateUseCase, reportRepository);
        verifyNoInteractions(maintenanceNoticePublisher);
    }

    @Test
    @DisplayName("Una habitación inexistente responde ROOM_NOT_FOUND sin consultar a Módulo 2")
    void unknownRoom() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.schedule(command(REASON, TODAY.plusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(reservationConflicts);
    }

    private void assertRule(LocalDate start, LocalDate end, String rule) {
        assertThatThrownBy(() -> service.schedule(command(REASON, start, end)))
                .isInstanceOfSatisfying(
                        InvalidDateRangeException.class,
                        ex -> assertThat(ex.rule()).isEqualTo(rule));
    }

    /** Habitación que pasa la validación, sin reservas, se bloquea en el mismo estado y no tiene cruces. */
    private Room givenRoom(RoomStatus status) {
        Room room = room(status);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        return room;
    }

    private void givenRoomForValidation(RoomStatus status) {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room(status)));
    }

    private void givenNoConflicts(LocalDate start, LocalDate end) {
        Room locked = roomRepository.findById(ROOM_ID).orElseThrow();
        when(reservationConflicts.checkMaintenanceConflict(ROOM_ID, start, end)).thenReturn(NO_RESERVATIONS);
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(locked));
        when(maintenanceAvailability.consult(ROOM_ID, start, end))
                .thenReturn(new MaintenanceAvailabilityDto(ROOM_ID, true, List.of()));
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TechnicalBlockReport savedReport() {
        ArgumentCaptor<TechnicalBlockReport> captor = ArgumentCaptor.forClass(TechnicalBlockReport.class);
        verify(reportRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ScheduleTechnicalBlockCommand command(String reason, LocalDate start, LocalDate end) {
        return new ScheduleTechnicalBlockCommand(ROOM_ID, MEMBER, reason, start, end);
    }

    private static Room room(RoomStatus status) {
        return new Room(ROOM_ID, "106", 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), status, null, 0);
    }
}

package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.DamageReportCreatedDto;
import com.hospitua.habitaciones.application.dto.ReportDamageCommand;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidDamageDescriptionException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportDamageServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String AUTHOR = "mtorres";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final OffsetDateTime PERIOD_START = OffsetDateTime.parse("2026-10-08T11:20:00-05:00");

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private DamageReportRepositoryPort damageReportRepository;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Mock
    private MaintenanceNoticePublisher maintenanceNoticePublisher;

    private Room room;
    private ReportDamageService service;

    @BeforeEach
    void setUp() {
        room = new Room(
                ROOM_ID, "101", 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), RoomStatus.Available, null, 0);
        service = serviceWith(Clock.fixed(PERIOD_START.toInstant(), BOGOTA));
    }

    @Test
    @DisplayName(
            "T004: inhabilita la habitación, crea el reporte del autor y publica SCHEDULED con la fecha del reporte")
    void reportDamage_disablesRoomAndPublishesNotice() {
        givenTransitionAt(PERIOD_START, SourceFlow.REPORT_DAMAGE);

        DamageReportCreatedDto created =
                service.reportDamage(command("  Ventana fisurada.  ", SourceFlow.REPORT_DAMAGE));

        DamageReport report = savedReport();
        assertThat(created.damageReportId()).isEqualTo(report.getId());
        assertThat(report.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(report.getAuthorId()).isEqualTo(AUTHOR);
        assertThat(report.getDamageDescription()).isEqualTo("Ventana fisurada.");
        LocalDate reportDate = LocalDate.of(2026, 10, 8);
        InOrder order =
                inOrder(roomRepository, transitionRoomStateUseCase, damageReportRepository, maintenanceNoticePublisher);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.DisabledForRepairs, AUTHOR, SourceFlow.REPORT_DAMAGE, null);
        order.verify(damageReportRepository).save(any());
        order.verify(maintenanceNoticePublisher).publishScheduled(report.getId(), room, reportDate, reportDate);
    }

    @Test
    @DisplayName("Desde el fin de limpieza con daño, la transición lleva CONFIRM_CLEANING_END")
    void reportDamage_fromCleaningEnd() {
        givenTransitionAt(PERIOD_START, SourceFlow.CONFIRM_CLEANING_END);

        service.reportDamage(command("Ventana fisurada.", SourceFlow.CONFIRM_CLEANING_END));

        verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.DisabledForRepairs, AUTHOR, SourceFlow.CONFIRM_CLEANING_END, null);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("T005: una descripción nula, vacía o solo con espacios se rechaza sin tocar la base")
    void reportDamage_blankDescription(String description) {
        assertRejectedWithoutChanges(description);
    }

    @Test
    @DisplayName("T005: una descripción de más de 500 caracteres se rechaza sin tocar la base")
    void reportDamage_tooLongDescription() {
        assertRejectedWithoutChanges("a".repeat(DamageReport.MAX_DESCRIPTION_LENGTH + 1));
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"Available"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("T006: fuera de Available se rechaza con el estado actual y sin reporte ni aviso")
    void reportDamage_notAvailable(RoomStatus status) {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        ROOM_ID, RoomStatus.DisabledForRepairs, AUTHOR, SourceFlow.REPORT_DAMAGE, null))
                .thenThrow(new InvalidRoomTransitionException(status));

        assertThatThrownBy(() -> service.reportDamage(command("Ventana fisurada.", SourceFlow.REPORT_DAMAGE)))
                .isInstanceOfSatisfying(
                        InvalidRoomTransitionException.class,
                        ex -> assertThat(ex.getCurrentStatus()).isEqualTo(status))
                .hasMessage(
                        "La habitación está " + status.displayName() + "; ahora no se puede reportar un daño en ella.");
        verifyNoInteractions(damageReportRepository, maintenanceNoticePublisher);
    }

    @Test
    @DisplayName("Si la habitación no existe responde ROOM_NOT_FOUND sin transición")
    void reportDamage_roomNotFound() {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportDamage(command("Ventana fisurada.", SourceFlow.REPORT_DAMAGE)))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(transitionRoomStateUseCase, damageReportRepository, maintenanceNoticePublisher);
    }

    @Test
    @DisplayName(
            "T007: reportDateTime es la marca del periodo, y la fecha del aviso es la de Bogotá aunque llegue en UTC")
    void reportDamage_reportDateTimeIsPeriodStart() {
        OffsetDateTime periodStartUtc = OffsetDateTime.parse("2026-10-09T03:30:00Z");
        service = serviceWith(Clock.fixed(periodStartUtc.toInstant(), BOGOTA));
        givenTransitionAt(periodStartUtc, SourceFlow.REPORT_DAMAGE);

        service.reportDamage(command("Ventana fisurada.", SourceFlow.REPORT_DAMAGE));

        DamageReport report = savedReport();
        assertThat(report.getReportDateTime()).isEqualTo(periodStartUtc);
        LocalDate bogotaDate = LocalDate.of(2026, 10, 8);
        verify(maintenanceNoticePublisher).publishScheduled(report.getId(), room, bogotaDate, bogotaDate);
    }

    @Test
    @DisplayName("El comando solo admite REPORT_DAMAGE y CONFIRM_CLEANING_END")
    void command_rejectsOtherSourceFlows() {
        assertThatThrownBy(() -> command("Ventana fisurada.", SourceFlow.CHECK_OUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRejectedWithoutChanges(String description) {
        assertThatThrownBy(() -> service.reportDamage(command(description, SourceFlow.REPORT_DAMAGE)))
                .isInstanceOf(InvalidDamageDescriptionException.class);
        verifyNoInteractions(
                roomRepository, transitionRoomStateUseCase, damageReportRepository, maintenanceNoticePublisher);
    }

    private void givenTransitionAt(OffsetDateTime periodStart, SourceFlow sourceFlow) {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(ROOM_ID, RoomStatus.DisabledForRepairs, AUTHOR, sourceFlow, null))
                .thenReturn(periodStart);
        when(damageReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private DamageReport savedReport() {
        ArgumentCaptor<DamageReport> captor = ArgumentCaptor.forClass(DamageReport.class);
        verify(damageReportRepository).save(captor.capture());
        return captor.getValue();
    }

    private ReportDamageService serviceWith(Clock clock) {
        return new ReportDamageService(
                roomRepository, damageReportRepository, transitionRoomStateUseCase, maintenanceNoticePublisher, clock);
    }

    private static ReportDamageCommand command(String description, SourceFlow sourceFlow) {
        return new ReportDamageCommand(ROOM_ID, description, AUTHOR, sourceFlow);
    }
}

package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ApplyScheduledTechnicalBlocksService} con su {@link ScheduledTechnicalBlockApplier} real y los puertos
 * simulados (plan-programar-bloqueo-tecnico-para-habitacion.md T015, parte de los servicios).
 */
@ExtendWith(MockitoExtension.class)
class ApplyScheduledTechnicalBlocksServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    private ApplyScheduledTechnicalBlocksService service;

    @BeforeEach
    void setUp() {
        service = new ApplyScheduledTechnicalBlocksService(
                reportRepository,
                new ScheduledTechnicalBlockApplier(roomRepository, reportRepository, transitionRoomStateUseCase));
    }

    @Test
    @DisplayName("T015: applyForRoom con la habitación Available y un Scheduled que cubre hoy lo aplica sin actor")
    void applyForRoom_available_applies() {
        givenLocked(ROOM_ID, RoomStatus.Available);
        TechnicalBlockReport report = scheduled(ROOM_ID, -1, 2);
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, TODAY)).thenReturn(List.of(report));

        assertThat(service.applyForRoom(ROOM_ID, TODAY)).isTrue();

        InOrder order = inOrder(roomRepository, transitionRoomStateUseCase, reportRepository);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.TechnicalBlock, null, SourceFlow.TECHNICAL_BLOCK, null);
        order.verify(reportRepository).save(report);
        assertThat(report.getStatus()).isEqualTo(TechnicalBlockStatus.Applied);
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "Available")
    @DisplayName("T015 y FR-016: fuera de Available (incluida Inactive) no hace nada y el informe sigue Scheduled")
    void applyForRoom_otherStatus_noop(RoomStatus status) {
        givenLocked(ROOM_ID, status);

        assertThat(service.applyForRoom(ROOM_ID, TODAY)).isFalse();

        verifyNoInteractions(transitionRoomStateUseCase);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("T015: sin un Scheduled que cubra hoy no hace nada (un Applied que se cruza no se vuelve a aplicar)")
    void applyForRoom_withoutScheduled_noop() {
        givenLocked(ROOM_ID, RoomStatus.Available);
        TechnicalBlockReport applied = scheduled(ROOM_ID, -1, 2);
        applied.apply();
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, TODAY)).thenReturn(List.of(applied));

        assertThat(service.applyForRoom(ROOM_ID, TODAY)).isFalse();

        verifyNoInteractions(transitionRoomStateUseCase);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una habitación que ya no existe no se aplica")
    void applyForRoom_unknownRoom_noop() {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.empty());

        assertThat(service.applyForRoom(ROOM_ID, TODAY)).isFalse();
        verifyNoInteractions(transitionRoomStateUseCase);
    }

    @Test
    @DisplayName("T015: runDaily primero caduca los vencidos y después aplica cada habitación con un Scheduled de hoy")
    void runDaily_expiresThenApplies() {
        UUID other = UUID.randomUUID();
        when(reportRepository.findRoomIdsWithScheduledCovering(TODAY)).thenReturn(List.of(ROOM_ID, other));
        givenLocked(ROOM_ID, RoomStatus.Available);
        givenLocked(other, RoomStatus.Occupied);
        TechnicalBlockReport report = scheduled(ROOM_ID, 0, 1);
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, TODAY)).thenReturn(List.of(report));

        service.runDaily(TODAY);

        InOrder order = inOrder(reportRepository, transitionRoomStateUseCase);
        order.verify(reportRepository).expireScheduledEndedBefore(TODAY);
        order.verify(reportRepository).findRoomIdsWithScheduledCovering(TODAY);
        order.verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.TechnicalBlock, null, SourceFlow.TECHNICAL_BLOCK, null);
        assertThat(report.getStatus()).isEqualTo(TechnicalBlockStatus.Applied);
        verify(roomRepository).lockById(other);
    }

    @Test
    @DisplayName("T015: repetir runDaily el mismo día no cambia nada más (ya no quedan Scheduled que cubran hoy)")
    void runDaily_idempotent() {
        when(reportRepository.findRoomIdsWithScheduledCovering(TODAY)).thenReturn(List.of());

        service.runDaily(TODAY);
        service.runDaily(TODAY);

        verifyNoInteractions(roomRepository, transitionRoomStateUseCase);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si una habitación falla, runDaily sigue con las demás")
    void runDaily_failureDoesNotStopOthers() {
        UUID failing = UUID.randomUUID();
        when(reportRepository.findRoomIdsWithScheduledCovering(TODAY)).thenReturn(List.of(failing, ROOM_ID));
        when(roomRepository.lockById(failing)).thenThrow(new IllegalStateException("fallo forzado"));
        givenLocked(ROOM_ID, RoomStatus.Available);
        TechnicalBlockReport report = scheduled(ROOM_ID, 0, 0);
        when(reportRepository.findActiveOverlapping(ROOM_ID, TODAY, TODAY)).thenReturn(List.of(report));

        service.runDaily(TODAY);

        assertThat(report.getStatus()).isEqualTo(TechnicalBlockStatus.Applied);
    }

    private void givenLocked(UUID roomId, RoomStatus status) {
        when(roomRepository.lockById(roomId))
                .thenReturn(Optional.of(
                        new Room(roomId, "106", 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), status, null, 0)));
    }

    private static TechnicalBlockReport scheduled(UUID roomId, int fromDays, int toDays) {
        return TechnicalBlockReport.schedule(
                roomId,
                "mtorres",
                "Cambio de tubería.",
                TODAY.plusDays(fromDays),
                TODAY.plusDays(toDays),
                OffsetDateTime.parse("2026-10-08T15:00:00-05:00"));
    }
}

package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.DailyReservationListMessageDto;
import com.hospitua.habitaciones.application.dto.DailyReservationUpdateMessageDto;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedResult;
import com.hospitua.habitaciones.application.event.DailyReservationListIngestedEvent;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationIngestionOutcome;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageType;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.DailyReservationUpdateType;
import com.hospitua.habitaciones.domain.model.MarkRoomReservedOutcome;
import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Ingesta de la cola diaria (plan-consultar-reservas.md T009 y T015, parte del servicio). */
@ExtendWith(MockitoExtension.class)
class DailyReservationIngestionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Mock
    private DailyReservationRepositoryPort reservationRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private MarkRoomReservedUseCase markRoomReserved;

    @Mock
    private MarkRoomAvailableUseCase markRoomAvailable;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DailyReservationIngestionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T05:00:02Z"), ZoneId.of("America/Bogota"));
        service = new DailyReservationIngestionService(
                reservationRepository, roomRepository, markRoomReserved, markRoomAvailable, eventPublisher, clock);
        lenient().when(reservationRepository.findLatestDailyListDate()).thenReturn(Optional.of(TODAY));
        lenient().when(reservationRepository.findLastSequence(any())).thenReturn(OptionalLong.of(1));
        lenient()
                .when(reservationRepository.findLastSequenceForReservation(any(), any()))
                .thenReturn(OptionalLong.empty());
        lenient()
                .when(markRoomReserved.markReserved(any()))
                .thenReturn(new MarkRoomReservedResult(MarkRoomReservedOutcome.RESERVED, RoomStatus.Reserved, null));
    }

    @Test
    @DisplayName("T009: un messageId ya registrado se descarta sin tocar la copia local ni las habitaciones")
    void duplicateMessage_isDiscarded() {
        when(reservationRepository.isMessageLogged("m-1")).thenReturn(true);

        assertThat(service.ingestDailyList(list("m-1", reservation("RSV-A", TODAY, room(1)))))
                .isEqualTo(DailyReservationIngestionOutcome.DUPLICATE);
        assertThat(service.ingestUpdate(update("m-1", 2, DailyReservationUpdateType.REMOVED, "RSV-A", null)))
                .isEqualTo(DailyReservationIngestionOutcome.DUPLICATE);

        verify(reservationRepository, never()).purge();
        verify(reservationRepository, never()).logMessage(any());
        verify(markRoomReserved, never()).markReserved(any());
    }

    @Test
    @DisplayName("T009 y T013: la lista de las 00:00 libera las huérfanas, purga y después aparta las nuevas")
    void dailyList_releasesThenPurgesThenReserves() {
        DailyReservationRoom orphan = room(1);
        DailyReservationRoom kept = room(2);
        DailyReservationRoom fresh = room(3);
        when(reservationRepository.findAll())
                .thenReturn(List.of(
                        reservation("RSV-OLD", TODAY.minusDays(1), orphan), reservation("RSV-KEPT", TODAY, kept)));
        when(roomRepository.lockById(orphan.roomId()))
                .thenReturn(Optional.of(roomInDb(orphan, RoomStatus.Reserved, "RSV-OLD")));
        DailyReservation keptToday = reservation("RSV-KEPT", TODAY, kept);
        DailyReservation freshToday = reservation("RSV-NEW", TODAY, fresh);

        DailyReservationIngestionOutcome outcome = service.ingestDailyList(list("m-list", keptToday, freshToday));

        assertThat(outcome).isEqualTo(DailyReservationIngestionOutcome.APPLIED);
        InOrder order = inOrder(markRoomAvailable, reservationRepository, markRoomReserved, eventPublisher);
        order.verify(markRoomAvailable)
                .markAvailable(
                        new MarkRoomAvailableCommand(orphan.roomId(), SourceFlow.MARK_AVAILABLE, null, "RSV-OLD"));
        order.verify(reservationRepository).purge();
        order.verify(reservationRepository).save(keptToday);
        order.verify(reservationRepository).save(freshToday);
        order.verify(markRoomReserved)
                .markReserved(new MarkRoomReservedCommand(kept.roomId(), "RSV-KEPT", ReservationTrigger.DAILY_LIST));
        order.verify(markRoomReserved)
                .markReserved(new MarkRoomReservedCommand(fresh.roomId(), "RSV-NEW", ReservationTrigger.DAILY_LIST));
        order.verify(reservationRepository)
                .logMessage(argThat(entry -> entry.messageType() == DailyReservationMessageType.DAILY_LIST
                        && entry.reservationRef() == null
                        && entry.operationalDate().equals(TODAY)));
        order.verify(eventPublisher).publishEvent(new DailyReservationListIngestedEvent(TODAY));
        // La habitación que sigue en su reserva no se libera: solo se bloquea una vez, para apartarla
        verify(roomRepository, times(1)).lockById(kept.roomId());
        verify(markRoomAvailable, times(1)).markAvailable(any());
    }

    @Test
    @DisplayName(
            "T009: una huérfana en limpieza, en otro estado o apartada por otra reserva solo cambia la copia local")
    void dailyList_doesNotReleaseRoomsNotReservedByTheReservation() {
        DailyReservationRoom cleaning = room(1);
        DailyReservationRoom otherReservation = room(2);
        DailyReservationRoom unknown = room(3);
        when(reservationRepository.findAll())
                .thenReturn(List.of(reservation("RSV-OLD", TODAY.minusDays(1), cleaning, otherReservation, unknown)));
        when(roomRepository.lockById(cleaning.roomId()))
                .thenReturn(Optional.of(roomInDb(cleaning, RoomStatus.InCleaning, null)));
        when(roomRepository.lockById(otherReservation.roomId()))
                .thenReturn(Optional.of(roomInDb(otherReservation, RoomStatus.Reserved, "RSV-OTRA")));
        when(roomRepository.lockById(unknown.roomId())).thenReturn(Optional.empty());

        service.ingestDailyList(list("m-list"));

        verify(markRoomAvailable, never()).markAvailable(any());
        verify(reservationRepository).purge();
    }

    @Test
    @DisplayName("T009: ante CONFLICT o ROOM_NOT_FOUND sigue con la siguiente habitación")
    void dailyList_continuesAfterConflictOrUnknownRoom() {
        DailyReservationRoom conflict = room(1);
        DailyReservationRoom unknown = room(2);
        DailyReservationRoom ok = room(3);
        when(reservationRepository.findAll()).thenReturn(List.of());
        when(markRoomReserved.markReserved(
                        argThat(command -> command != null && command.roomId().equals(conflict.roomId()))))
                .thenReturn(new MarkRoomReservedResult(MarkRoomReservedOutcome.CONFLICT, RoomStatus.Occupied, null));
        when(markRoomReserved.markReserved(
                        argThat(command -> command != null && command.roomId().equals(unknown.roomId()))))
                .thenReturn(new MarkRoomReservedResult(MarkRoomReservedOutcome.ROOM_NOT_FOUND, null, null));

        assertThat(service.ingestDailyList(list("m-list", reservation("RSV-A", TODAY, conflict, unknown, ok))))
                .isEqualTo(DailyReservationIngestionOutcome.APPLIED);

        verify(markRoomReserved, times(3)).markReserved(any());
        verify(roomRepository).lockById(ok.roomId());
    }

    @Test
    @DisplayName("T009: un ADDED de hoy aparta con ADDED_UPDATE; uno de otro día solo se guarda")
    void added_reservesOnlyWhenArrivingToday() {
        DailyReservationRoom today = room(1);
        DailyReservationRoom tomorrow = room(2);
        DailyReservation arrivingToday = reservation("RSV-HOY", TODAY, today);
        DailyReservation arrivingTomorrow = reservation("RSV-MANANA", TODAY.plusDays(1), tomorrow);

        service.ingestUpdate(update("m-2", 2, DailyReservationUpdateType.ADDED, "RSV-HOY", arrivingToday));
        service.ingestUpdate(update("m-3", 3, DailyReservationUpdateType.ADDED, "RSV-MANANA", arrivingTomorrow));

        verify(reservationRepository).save(arrivingToday);
        verify(reservationRepository).save(arrivingTomorrow);
        verify(markRoomReserved)
                .markReserved(new MarkRoomReservedCommand(today.roomId(), "RSV-HOY", ReservationTrigger.ADDED_UPDATE));
        verify(markRoomReserved, never())
                .markReserved(
                        argThat(command -> command != null && command.roomId().equals(tomorrow.roomId())));
        verify(reservationRepository)
                .logMessage(argThat(entry -> entry.messageType() == DailyReservationMessageType.UPDATE
                        && "RSV-HOY".equals(entry.reservationRef())
                        && entry.sequenceNumber() == 2));
    }

    @Test
    @DisplayName(
            "T009: un UPDATED que cambia la habitación libera la anterior apartada por la reserva y aparta la nueva")
    void updated_changingRoom_releasesOldAndReservesNew() {
        DailyReservationRoom oldRoom = room(1);
        DailyReservationRoom newRoom = room(2);
        when(reservationRepository.findByReservationRef("RSV-A"))
                .thenReturn(Optional.of(reservation("RSV-A", TODAY, oldRoom)));
        when(roomRepository.lockById(oldRoom.roomId()))
                .thenReturn(Optional.of(roomInDb(oldRoom, RoomStatus.Reserved, "RSV-A")));
        DailyReservation updated = reservation("RSV-A", TODAY, newRoom);

        service.ingestUpdate(update("m-4", 2, DailyReservationUpdateType.UPDATED, "RSV-A", updated));

        InOrder order = inOrder(markRoomAvailable, reservationRepository, markRoomReserved);
        order.verify(markRoomAvailable)
                .markAvailable(
                        new MarkRoomAvailableCommand(oldRoom.roomId(), SourceFlow.MARK_AVAILABLE, null, "RSV-A"));
        order.verify(reservationRepository).save(updated);
        order.verify(markRoomReserved)
                .markReserved(new MarkRoomReservedCommand(newRoom.roomId(), "RSV-A", ReservationTrigger.ADDED_UPDATE));
    }

    @Test
    @DisplayName("T009: un REMOVED quita la reserva y libera solo las habitaciones apartadas por ella")
    void removed_deletesAndReleasesReservedRooms() {
        DailyReservationRoom reserved = room(1);
        DailyReservationRoom cleaning = room(2);
        when(reservationRepository.findByReservationRef("RSV-A"))
                .thenReturn(Optional.of(reservation("RSV-A", TODAY, reserved, cleaning)));
        when(roomRepository.lockById(reserved.roomId()))
                .thenReturn(Optional.of(roomInDb(reserved, RoomStatus.Reserved, "RSV-A")));
        when(roomRepository.lockById(cleaning.roomId()))
                .thenReturn(Optional.of(roomInDb(cleaning, RoomStatus.InCleaning, null)));

        assertThat(service.ingestUpdate(update("m-5", 2, DailyReservationUpdateType.REMOVED, "RSV-A", null)))
                .isEqualTo(DailyReservationIngestionOutcome.APPLIED);

        verify(reservationRepository).deleteByReservationRef("RSV-A");
        verify(markRoomAvailable)
                .markAvailable(
                        new MarkRoomAvailableCommand(reserved.roomId(), SourceFlow.MARK_AVAILABLE, null, "RSV-A"));
        verify(markRoomAvailable, times(1)).markAvailable(any());
    }

    @Test
    @DisplayName(
            "T009: una actualización con secuencia menor o igual a la última de la reserva se registra sin aplicarse")
    void outOfOrderUpdate_isLoggedButNotApplied() {
        when(reservationRepository.findLastSequenceForReservation(TODAY, "RSV-A"))
                .thenReturn(OptionalLong.of(5));
        DailyReservation older = reservation("RSV-A", TODAY, room(1));

        assertThat(service.ingestUpdate(update("m-4", 4, DailyReservationUpdateType.UPDATED, "RSV-A", older)))
                .isEqualTo(DailyReservationIngestionOutcome.OUTDATED_SEQUENCE);
        assertThat(service.ingestUpdate(update("m-5b", 5, DailyReservationUpdateType.REMOVED, "RSV-A", null)))
                .isEqualTo(DailyReservationIngestionOutcome.OUTDATED_SEQUENCE);

        verify(reservationRepository, never()).save(any());
        verify(reservationRepository, never()).deleteByReservationRef(any());
        verify(reservationRepository, times(2)).logMessage(any(DailyReservationMessageLog.class));
    }

    @Test
    @DisplayName("T015: un mensaje de un día anterior al de la copia local se registra sin aplicarse")
    void previousDayMessage_isLoggedButNotApplied() {
        LocalDate yesterday = TODAY.minusDays(1);

        DailyReservationIngestionOutcome removed = service.ingestUpdate(new DailyReservationUpdateMessageDto(
                "m-old", 9, yesterday, DailyReservationUpdateType.REMOVED, "RSV-A", null));
        DailyReservationIngestionOutcome list =
                service.ingestDailyList(new DailyReservationListMessageDto("m-old-list", 1, yesterday, List.of()));

        assertThat(removed).isEqualTo(DailyReservationIngestionOutcome.OUTDATED_DAY);
        assertThat(list).isEqualTo(DailyReservationIngestionOutcome.OUTDATED_DAY);
        verify(reservationRepository, never()).deleteByReservationRef(any());
        verify(reservationRepository, never()).purge();
        verify(markRoomAvailable, never()).markAvailable(any());
        verify(reservationRepository)
                .logMessage(argThat(entry -> entry.messageId().equals("m-old")
                        && entry.operationalDate().equals(yesterday)));
    }

    @Test
    @DisplayName("T015: un sequenceNumber mayor que el siguiente esperado se aplica igual (SEQUENCE_GAP)")
    void sequenceGap_isAppliedAnyway() {
        DailyReservation added = reservation("RSV-A", TODAY, room(1));

        assertThat(service.ingestUpdate(update("m-9", 9, DailyReservationUpdateType.ADDED, "RSV-A", added)))
                .isEqualTo(DailyReservationIngestionOutcome.APPLIED);

        verify(reservationRepository).save(added);
    }

    @Test
    @DisplayName("T009: sin lista del día recibida, un ADDED de hoy también se aplica")
    void addedWithoutDailyList_isApplied() {
        when(reservationRepository.findLatestDailyListDate()).thenReturn(Optional.empty());
        when(reservationRepository.findLastSequence(TODAY)).thenReturn(OptionalLong.empty());
        DailyReservation added = reservation("RSV-A", TODAY, room(1));

        assertThat(service.ingestUpdate(update("m-2", 2, DailyReservationUpdateType.ADDED, "RSV-A", added)))
                .isEqualTo(DailyReservationIngestionOutcome.APPLIED);
    }

    @Test
    @DisplayName("T009: la ingesta no depende de TransitionRoomStateUseCase (FR-009)")
    void ingestion_doesNotUseTransitionPortDirectly() {
        assertThat(Arrays.stream(DailyReservationIngestionService.class.getDeclaredFields())
                        .map(Field::getType))
                .doesNotContain(TransitionRoomStateUseCase.class);
    }

    private static DailyReservationListMessageDto list(String messageId, DailyReservation... reservations) {
        return new DailyReservationListMessageDto(messageId, 1, TODAY, List.of(reservations));
    }

    private static DailyReservationUpdateMessageDto update(
            String messageId,
            long sequence,
            DailyReservationUpdateType type,
            String reservationRef,
            DailyReservation reservation) {
        return new DailyReservationUpdateMessageDto(messageId, sequence, TODAY, type, reservationRef, reservation);
    }

    private static DailyReservation reservation(String ref, LocalDate startDate, DailyReservationRoom... rooms) {
        return new DailyReservation(
                ref,
                "Ana",
                "Pérez",
                "CC",
                "123456",
                "Colombia",
                "BOOKING",
                startDate,
                startDate.plusDays(3),
                2,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                List.of(rooms));
    }

    private static DailyReservationRoom room(int i) {
        return new DailyReservationRoom(UUID.randomUUID(), String.valueOf(200 + i), "DOBLE", 2);
    }

    private static Room roomInDb(DailyReservationRoom room, RoomStatus status, String reservedBy) {
        return new Room(
                room.roomId(),
                room.roomNumber(),
                2,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("200000"),
                status,
                reservedBy,
                0);
    }
}

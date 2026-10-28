package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedResult;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.MarkRoomReservedOutcome;
import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkRoomReservedServiceTest {

    @Mock
    private RoomRepositoryPort roomRepositoryPort;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Mock
    private RoomAuditLogRepositoryPort roomAuditLogRepositoryPort;

    @InjectMocks
    private MarkRoomReservedService service;

    private Room createRoom(UUID id, RoomStatus status, String reservationRef) {
        return new Room(id, "101", 1, RoomCategory.SENCILLA, 1, new BigDecimal("100.00"), status, reservationRef, 0);
    }

    @Test
    @DisplayName("T004: Available -> Reserved retorna RESERVED e invoca transition")
    void shouldMarkReservedWhenRoomIsAvailable() {
        UUID roomId = UUID.randomUUID();
        String ref = "RES-101";
        Room room = createRoom(roomId, RoomStatus.Available, null);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        eq(roomId), eq(RoomStatus.Reserved), isNull(), eq(SourceFlow.MARK_RESERVED), eq(ref)))
                .thenReturn(OffsetDateTime.now());

        MarkRoomReservedCommand command = new MarkRoomReservedCommand(roomId, ref, ReservationTrigger.DAILY_LIST);
        MarkRoomReservedResult result = service.markReserved(command);

        assertThat(result.outcome()).isEqualTo(MarkRoomReservedOutcome.RESERVED);
        assertThat(result.currentStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(result.linkedReservationRef()).isEqualTo(ref);

        verify(transitionRoomStateUseCase).transition(roomId, RoomStatus.Reserved, null, SourceFlow.MARK_RESERVED, ref);
        verify(roomAuditLogRepositoryPort, never()).record(any());
    }

    @Test
    @DisplayName("T005: Falta roomId o reservationRef lanza IllegalArgumentException")
    void shouldThrowWhenRequiredFieldsAreMissing() {
        UUID roomId = UUID.randomUUID();
        ReservationTrigger trigger = ReservationTrigger.DAILY_LIST;

        assertThatThrownBy(() -> new MarkRoomReservedCommand(null, "RES-101", trigger))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MarkRoomReservedCommand(roomId, null, trigger))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MarkRoomReservedCommand(roomId, "   ", trigger))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T008: Idempotencia - ya Reserved con la misma referencia retorna ALREADY_RESERVED")
    void shouldReturnAlreadyReservedWhenSameReservationRef() {
        UUID roomId = UUID.randomUUID();
        String ref = "RES-101";
        Room room = createRoom(roomId, RoomStatus.Reserved, ref);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkRoomReservedCommand command = new MarkRoomReservedCommand(roomId, ref, ReservationTrigger.ADDED_UPDATE);
        MarkRoomReservedResult result = service.markReserved(command);

        assertThat(result.outcome()).isEqualTo(MarkRoomReservedOutcome.ALREADY_RESERVED);
        assertThat(result.currentStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(result.linkedReservationRef()).isEqualTo(ref);

        verify(transitionRoomStateUseCase, never()).transition(any(), any(), any(), any(), any());
        verify(roomAuditLogRepositoryPort, never()).record(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock", "Inactive"})
    @DisplayName("T009: Estados no aptos retornan CONFLICT y registran auditoría")
    void shouldReturnConflictForIncompatibleStatuses(RoomStatus nonAvailableStatus) {
        UUID roomId = UUID.randomUUID();
        String ref = "RES-999";
        Room room = createRoom(roomId, nonAvailableStatus, null);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkRoomReservedCommand command = new MarkRoomReservedCommand(roomId, ref, ReservationTrigger.DAILY_LIST);
        MarkRoomReservedResult result = service.markReserved(command);

        assertThat(result.outcome()).isEqualTo(MarkRoomReservedOutcome.CONFLICT);
        assertThat(result.currentStatus()).isEqualTo(nonAvailableStatus);
        assertThat(result.linkedReservationRef()).isNull();

        ArgumentCaptor<RoomAuditEntry> captor = ArgumentCaptor.forClass(RoomAuditEntry.class);
        verify(roomAuditLogRepositoryPort).record(captor.capture());
        RoomAuditEntry recordedEntry = captor.getValue();

        assertThat(recordedEntry.roomId()).isEqualTo(roomId);
        assertThat(recordedEntry.eventType()).isEqualTo(RoomAuditEventType.RESERVATION_STATE_CONFLICT);
        assertThat(recordedEntry.actorId()).isNull();
        assertThat(recordedEntry.details()).containsEntry("currentStatus", nonAvailableStatus.name());
        assertThat(recordedEntry.details()).containsEntry("incomingReservationRef", ref);
        assertThat(recordedEntry.details()).containsEntry("trigger", ReservationTrigger.DAILY_LIST.name());

        verify(transitionRoomStateUseCase, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("T010: Reserved para otra reserva retorna CONFLICT y registra ambas referencias")
    void shouldReturnConflictWhenReservedForDifferentReservation() {
        UUID roomId = UUID.randomUUID();
        String currentRef = "RES-101";
        String incomingRef = "RES-202";
        Room room = createRoom(roomId, RoomStatus.Reserved, currentRef);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkRoomReservedCommand command =
                new MarkRoomReservedCommand(roomId, incomingRef, ReservationTrigger.DAILY_LIST);
        MarkRoomReservedResult result = service.markReserved(command);

        assertThat(result.outcome()).isEqualTo(MarkRoomReservedOutcome.CONFLICT);
        assertThat(result.currentStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(result.linkedReservationRef()).isEqualTo(currentRef);

        ArgumentCaptor<RoomAuditEntry> captor = ArgumentCaptor.forClass(RoomAuditEntry.class);
        verify(roomAuditLogRepositoryPort).record(captor.capture());
        RoomAuditEntry recordedEntry = captor.getValue();

        assertThat(recordedEntry.roomId()).isEqualTo(roomId);
        assertThat(recordedEntry.eventType()).isEqualTo(RoomAuditEventType.RESERVATION_STATE_CONFLICT);
        assertThat(recordedEntry.details()).containsEntry("currentStatus", RoomStatus.Reserved.name());
        assertThat(recordedEntry.details()).containsEntry("incomingReservationRef", incomingRef);
        assertThat(recordedEntry.details()).containsEntry("linkedReservationRef", currentRef);
        assertThat(recordedEntry.details()).containsEntry("trigger", ReservationTrigger.DAILY_LIST.name());
    }

    @Test
    @DisplayName("T011: Habitación no encontrada devuelve ROOM_NOT_FOUND")
    void shouldReturnRoomNotFoundWhenRoomDoesNotExist() {
        UUID roomId = UUID.randomUUID();
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.empty());

        MarkRoomReservedCommand command = new MarkRoomReservedCommand(roomId, "RES-101", ReservationTrigger.DAILY_LIST);
        MarkRoomReservedResult result = service.markReserved(command);

        assertThat(result.outcome()).isEqualTo(MarkRoomReservedOutcome.ROOM_NOT_FOUND);
        assertThat(result.currentStatus()).isNull();
        assertThat(result.linkedReservationRef()).isNull();

        verify(transitionRoomStateUseCase, never()).transition(any(), any(), any(), any(), any());
        verify(roomAuditLogRepositoryPort, never()).record(any());
    }
}

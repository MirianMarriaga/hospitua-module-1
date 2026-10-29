package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomStateTransitionServiceTest {

    @Mock
    private RoomRepositoryPort roomRepositoryPort;

    @Mock
    private RoomStateHistoryRecorder historyRecorder;

    @InjectMocks
    private RoomStateTransitionService transitionService;

    @Test
    @DisplayName("Transición válida actualiza estado, guarda habitación y registra historial")
    void validTransitionShouldUpdateRoomAndRecordHistory() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room(
                roomId, "101", 1, RoomCategory.SENCILLA, 2, new BigDecimal("100.00"), RoomStatus.Available, null, 0);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        OffsetDateTime expectedTimestamp = OffsetDateTime.now();
        when(historyRecorder.recordTransition(
                        eq(roomId),
                        eq(RoomStatus.InCleaning),
                        eq(RoomStatus.Available),
                        eq("user1"),
                        eq(SourceFlow.START_CLEANING),
                        isNull()))
                .thenReturn(expectedTimestamp);

        OffsetDateTime result =
                transitionService.transition(roomId, RoomStatus.InCleaning, "user1", SourceFlow.START_CLEANING, null);

        assertThat(result).isEqualTo(expectedTimestamp);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.InCleaning);
        verify(roomRepositoryPort).save(room);
        verify(historyRecorder)
                .recordTransition(
                        roomId, RoomStatus.InCleaning, RoomStatus.Available, "user1", SourceFlow.START_CLEANING, null);
    }

    @Test
    @DisplayName("Transición a Reserved con referencia válida asigna reserva y registra historial")
    void transitionToReservedWithValidRefShouldReserveRoom() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room(
                roomId, "101", 1, RoomCategory.SENCILLA, 2, new BigDecimal("100.00"), RoomStatus.Available, null, 0);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        OffsetDateTime expectedTimestamp = OffsetDateTime.now();
        when(historyRecorder.recordTransition(
                        eq(roomId),
                        eq(RoomStatus.Reserved),
                        eq(RoomStatus.Available),
                        isNull(),
                        eq(SourceFlow.MARK_RESERVED),
                        eq("RES-999")))
                .thenReturn(expectedTimestamp);

        OffsetDateTime result =
                transitionService.transition(roomId, RoomStatus.Reserved, null, SourceFlow.MARK_RESERVED, "RES-999");

        assertThat(result).isEqualTo(expectedTimestamp);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(room.getReservedByReservationRef()).isEqualTo("RES-999");
        verify(roomRepositoryPort).save(room);
    }

    @Test
    @DisplayName("Destino Reserved sin referencia: lo rechaza Room.reserveFor y no se guarda nada")
    void transitionToReservedWithoutReferenceShouldBeRejected() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room(
                roomId, "101", 1, RoomCategory.SENCILLA, 2, new BigDecimal("100.00"), RoomStatus.Available, null, 0);
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> transitionService.transition(
                        roomId, RoomStatus.Reserved, "user1", SourceFlow.MARK_RESERVED, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> transitionService.transition(
                        roomId, RoomStatus.Reserved, "user1", SourceFlow.MARK_RESERVED, "   "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomRepositoryPort, never()).save(any());
        verifyNoInteractions(historyRecorder);
    }

    @Test
    @DisplayName("Habitación no encontrada debe lanzar RoomNotFoundException")
    void nonExistentRoomShouldThrowRoomNotFoundException() {
        UUID roomId = UUID.randomUUID();
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transitionService.transition(
                        roomId, RoomStatus.InCleaning, "user1", SourceFlow.START_CLEANING, null))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("registerInitialStatus registra el periodo con previous_status nulo")
    void registerInitialStatusShouldRecordInitialPeriodWithNullPreviousStatus() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room(
                roomId, "201", 2, RoomCategory.DOBLE, 2, new BigDecimal("150.00"), RoomStatus.Available, null, 0);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        OffsetDateTime expectedTimestamp = OffsetDateTime.now();
        when(historyRecorder.recordTransition(
                        eq(roomId),
                        eq(RoomStatus.Available),
                        isNull(),
                        eq("admin1"),
                        eq(SourceFlow.REGISTER_ROOM),
                        isNull()))
                .thenReturn(expectedTimestamp);

        OffsetDateTime result = transitionService.registerInitialStatus(roomId, "admin1", SourceFlow.REGISTER_ROOM);

        assertThat(result).isEqualTo(expectedTimestamp);
        verify(historyRecorder)
                .recordTransition(roomId, RoomStatus.Available, null, "admin1", SourceFlow.REGISTER_ROOM, null);
    }
}

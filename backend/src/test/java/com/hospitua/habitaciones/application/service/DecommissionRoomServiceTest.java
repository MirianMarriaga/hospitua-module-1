package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.DecommissionRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDecommissionDto;
import com.hospitua.habitaciones.application.ports.in.CheckRoomReservationConflictsUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.DomainError;
import com.hospitua.habitaciones.domain.exception.ExternalModule2UnavailableException;
import com.hospitua.habitaciones.domain.exception.InvalidDecommissionReasonException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.ReservationConflictException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

/** {@link RoomDecommissionRegistration} es real, sobre los mismos mocks: así se ve el orden completo de la baja. */
@ExtendWith(MockitoExtension.class)
class DecommissionRoomServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String ADMIN = "lmendez";
    private static final OffsetDateTime PERIOD_START = OffsetDateTime.parse("2026-10-09T15:00:00-05:00");
    private static final ReservationConflictResult NO_CONFLICTS = new ReservationConflictResult(List.of());

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private CheckRoomReservationConflictsUseCase reservationConflicts;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Mock
    private RoomDecommissionRepositoryPort decommissionRepository;

    private DecommissionRoomService service;

    @BeforeEach
    void setUp() {
        service = new DecommissionRoomService(
                roomRepository,
                reservationConflicts,
                new RoomDecommissionRegistration(roomRepository, transitionRoomStateUseCase, decommissionRepository));
    }

    @Test
    @DisplayName("T005: sin reservas, la habitación pasa a Inactive con DECOMMISSION_ROOM y se registra la baja")
    void decommission_withoutReservations_registersDecommission() {
        Room room = givenRoom(RoomStatus.Available);
        when(reservationConflicts.checkDecommissionConflict(ROOM_ID)).thenReturn(NO_CONFLICTS);
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        ROOM_ID, RoomStatus.Inactive, ADMIN, SourceFlow.DECOMMISSION_ROOM, null))
                .thenReturn(PERIOD_START);
        when(decommissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoomDecommissionDto result =
                service.decommission(command(DecommissionReason.FLOOR_CLOSURE, "  Cierre del piso 3.  "));

        RoomDecommission saved = savedDecommission();
        assertThat(saved.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(saved.getReasonCategory()).isEqualTo(DecommissionReason.FLOOR_CLOSURE);
        assertThat(saved.getReasonDetail()).isEqualTo("Cierre del piso 3.");
        assertThat(saved.getDecommissionedBy()).isEqualTo(ADMIN);
        assertThat(saved.getDecommissionDateTime()).isEqualTo(PERIOD_START);
        assertThat(result)
                .isEqualTo(new RoomDecommissionDto(
                        saved.getId(),
                        ROOM_ID,
                        "305",
                        3,
                        RoomCategory.SUITE,
                        RoomStatus.Inactive,
                        DecommissionReason.FLOOR_CLOSURE,
                        "Cierre del piso 3.",
                        PERIOD_START));
        InOrder order =
                inOrder(roomRepository, reservationConflicts, transitionRoomStateUseCase, decommissionRepository);
        order.verify(roomRepository).findById(ROOM_ID);
        order.verify(reservationConflicts).checkDecommissionConflict(ROOM_ID);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.Inactive, ADMIN, SourceFlow.DECOMMISSION_ROOM, null);
        order.verify(decommissionRepository).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = "Available", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("T006: fuera de Available responde ROOM_INVALID_STATE sin consultar a Módulo 2 ni escribir")
    void decommission_notAvailable_rejectedWithoutModule2(RoomStatus status) {
        givenRoom(status);

        assertThatThrownBy(() -> service.decommission(command(DecommissionReason.FLOOR_CLOSURE, null)))
                .isInstanceOfSatisfying(InvalidRoomTransitionException.class, ex -> {
                    assertThat(ex.getCurrentStatus()).isEqualTo(status);
                    assertThat(ex.getMessage())
                            .isEqualTo(
                                    status == RoomStatus.Occupied
                                            ? "La habitación tiene un huésped activo; debe estar Disponible para darla"
                                                    + " de baja."
                                            : "No es posible dar de baja la habitación: está en estado "
                                                    + status.displayName() + " y debe estar Disponible.");
                });
        verifyNoInteractions(reservationConflicts, transitionRoomStateUseCase, decommissionRepository);
    }

    @Test
    @DisplayName("T007 y T008: cualquier reserva devuelta, aunque sea a meses, rechaza la baja con todas listadas")
    void decommission_withReservations_rejected() {
        givenRoom(RoomStatus.Available);
        LocalDate today = PERIOD_START.toLocalDate();
        List<ConflictingReservation> reservations = List.of(
                new ConflictingReservation("RES-10502", "ACTIVE", today.plusDays(15), today.plusDays(18)),
                new ConflictingReservation("RES-10731", "PENDING", today.plusDays(300), today.plusDays(302)));
        when(reservationConflicts.checkDecommissionConflict(ROOM_ID))
                .thenReturn(new ReservationConflictResult(reservations));

        assertThatThrownBy(() -> service.decommission(command(DecommissionReason.FLOOR_CLOSURE, null)))
                .isInstanceOfSatisfying(ReservationConflictException.class, ex -> {
                    assertThat(ex.error()).isEqualTo(DomainError.RESERVATION_CONFLICT);
                    assertThat(ex.getMessage())
                            .isEqualTo(
                                    "La habitación tiene reservas vigentes. Deben reasignarse antes de darla de baja.");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> listed =
                            (List<Map<String, Object>>) ex.details().get("reservations");
                    assertThat(listed)
                            .extracting(r -> r.get("reservationRef"))
                            .containsExactly("RES-10502", "RES-10731");
                });
        verifyNoInteractions(transitionRoomStateUseCase, decommissionRepository);
    }

    @Test
    @DisplayName("T009: si Módulo 2 no responde no se modifica nada; repetir la petición vuelve a verificar")
    void decommission_module2Unavailable_retryChecksAgain() {
        givenRoom(RoomStatus.Available);
        when(reservationConflicts.checkDecommissionConflict(ROOM_ID))
                .thenThrow(new ExternalModule2UnavailableException(new IllegalStateException("timeout")))
                .thenReturn(NO_CONFLICTS);
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room(RoomStatus.Available)));
        when(transitionRoomStateUseCase.transition(any(), any(), any(), any(), any()))
                .thenReturn(PERIOD_START);
        when(decommissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DecommissionRoomCommand command = command(DecommissionReason.ADMINISTRATIVE_DECISION, null);

        assertThatThrownBy(() -> service.decommission(command)).isInstanceOf(ExternalModule2UnavailableException.class);
        verifyNoInteractions(transitionRoomStateUseCase, decommissionRepository);

        assertThat(service.decommission(command).status()).isEqualTo(RoomStatus.Inactive);
    }

    @Test
    @DisplayName("T010: OTHER sin detalle se rechaza antes de leer la habitación y de consultar a Módulo 2")
    void decommission_otherWithoutDetail_rejectedFirst() {
        assertThatThrownBy(() -> service.decommission(command(DecommissionReason.OTHER, "   ")))
                .isInstanceOf(InvalidDecommissionReasonException.class)
                .hasMessage("Escribe el motivo de la baja.");
        verifyNoInteractions(roomRepository, reservationConflicts, transitionRoomStateUseCase, decommissionRepository);
    }

    @Test
    @DisplayName("Una habitación inexistente responde ROOM_NOT_FOUND sin consultar a Módulo 2")
    void decommission_unknownRoom() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decommission(command(DecommissionReason.FLOOR_CLOSURE, null)))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(reservationConflicts);
    }

    @Test
    @DisplayName("Regla 13: si la habitación cambió mientras Módulo 2 respondía, se rechaza con su estado y sin baja")
    void decommission_roomChangedDuringCheck_rejected() {
        givenRoom(RoomStatus.Available);
        when(reservationConflicts.checkDecommissionConflict(ROOM_ID)).thenReturn(NO_CONFLICTS);
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room(RoomStatus.Reserved)));
        when(transitionRoomStateUseCase.transition(
                        ROOM_ID, RoomStatus.Inactive, ADMIN, SourceFlow.DECOMMISSION_ROOM, null))
                .thenThrow(new InvalidRoomTransitionException(RoomStatus.Reserved));

        assertThatThrownBy(() -> service.decommission(command(DecommissionReason.FLOOR_CLOSURE, null)))
                .isInstanceOfSatisfying(InvalidRoomTransitionException.class, ex -> {
                    assertThat(ex.getCurrentStatus()).isEqualTo(RoomStatus.Reserved);
                    assertThat(ex.getMessage())
                            .isEqualTo("La habitación cambió de estado (estado actual: Reservada); no se dio de baja.");
                });
        verifyNoInteractions(decommissionRepository);
    }

    private Room givenRoom(RoomStatus status) {
        Room room = room(status);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        return room;
    }

    private static Room room(RoomStatus status) {
        return new Room(ROOM_ID, "305", 3, RoomCategory.SUITE, 4, new BigDecimal("320000"), status, null, 0);
    }

    private static DecommissionRoomCommand command(DecommissionReason reason, String detail) {
        return new DecommissionRoomCommand(ROOM_ID, reason, detail, ADMIN);
    }

    private RoomDecommission savedDecommission() {
        ArgumentCaptor<RoomDecommission> captor = ArgumentCaptor.forClass(RoomDecommission.class);
        verify(decommissionRepository).save(captor.capture());
        return captor.getValue();
    }
}

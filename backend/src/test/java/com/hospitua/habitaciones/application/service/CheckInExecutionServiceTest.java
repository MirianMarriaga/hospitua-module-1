package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.CheckInMessagePayload;
import com.hospitua.habitaciones.application.dto.CheckInResultDto;
import com.hospitua.habitaciones.application.dto.RegisterCheckInCommand;
import com.hospitua.habitaciones.application.dto.RegisterCheckInCommand.GuestInput;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.exception.GuestCountMismatchException;
import com.hospitua.habitaciones.domain.exception.InvalidGuestDataException;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.MovementType;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.Stay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInExecutionServiceTest {

    // 23:30 del 9-10 en Bogotá: en UTC ya es 10-10, así que la fecha del Check-In debe salir del reloj de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate BIRTH = LocalDate.of(1990, 5, 20);
    private static final String REF = "RSV-8D02E5A4";
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private DailyReservationRepositoryPort reservationRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private StayRepositoryPort stayRepository;

    @Mock
    private RoomGuestRepositoryPort roomGuestRepository;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Mock
    private RoomAuditLogRepositoryPort auditLog;

    @Mock
    private OutboxEventPublisherPort outbox;

    private CheckInExecutionService service;

    @BeforeEach
    void setUp() {
        service = new CheckInExecutionService(
                reservationRepository,
                roomRepository,
                stayRepository,
                roomGuestRepository,
                transitionRoomStateUseCase,
                auditLog,
                outbox,
                CLOCK);
    }

    @Test
    @DisplayName("T012: crea la estancia y sus huéspedes, pasa a Occupied por CHECK_IN, audita y encola el ENTRY")
    void registerCheckIn_persistsTransitionsAuditsAndEnqueues() {
        givenEligibleReservation();
        when(stayRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckInResultDto result = service.registerCheckIn(command(List.of(
                colombian("CC", "123", true),
                new GuestInput("John", "Smith", "PAS", "X99", "Perú", BIRTH, "Lima", "Cartagena", false))));

        ArgumentCaptor<Stay> stay = ArgumentCaptor.forClass(Stay.class);
        verify(stayRepository).save(stay.capture());
        assertThat(stay.getValue().getReservationRef()).isEqualTo(REF);
        assertThat(stay.getValue().getRoomId()).isEqualTo(ROOM_ID);
        assertThat(stay.getValue().getSource()).isEqualTo("BOOKING");
        assertThat(stay.getValue().getCheckInDate()).isEqualTo(TODAY);
        assertThat(stay.getValue().getCheckOutDate()).isNull();
        assertThat(stay.getValue().getExpectedCheckoutDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(stay.getValue().getTitularDocumentNumber()).isEqualTo("123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoomGuest>> guests = ArgumentCaptor.forClass(List.class);
        verify(roomGuestRepository).saveAll(guests.capture());
        assertThat(guests.getValue())
                .extracting(RoomGuest::getStayId, RoomGuest::getDocumentNumber, RoomGuest::isReservationGuest)
                .containsExactly(
                        tuple(stay.getValue().getId(), "123", true),
                        tuple(stay.getValue().getId(), "X99", false));

        InOrder order = inOrder(stayRepository, transitionRoomStateUseCase, auditLog, outbox);
        order.verify(stayRepository).save(any());
        order.verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.Occupied, "lmendez", SourceFlow.CHECK_IN, REF);
        ArgumentCaptor<RoomAuditEntry> audit = ArgumentCaptor.forClass(RoomAuditEntry.class);
        order.verify(auditLog).record(audit.capture());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        order.verify(outbox).enqueue(eq("habitacion.checkin"), eq("CHECK_IN"), payload.capture());

        assertThat(audit.getValue().eventType()).isEqualTo(RoomAuditEventType.CHECK_IN);
        assertThat(audit.getValue().actorId()).isEqualTo("lmendez");
        assertThat(audit.getValue().details())
                .containsEntry("stayId", stay.getValue().getId().toString())
                .containsEntry("reservationRef", REF);

        CheckInMessagePayload message = (CheckInMessagePayload) payload.getValue();
        assertThat(message.reservationRef()).isEqualTo(REF);
        assertThat(message.roomId()).isEqualTo(ROOM_ID);
        assertThat(message.movementType()).isEqualTo(MovementType.ENTRY);
        assertThat(message.movementDate()).isEqualTo(TODAY);
        assertThat(message.guests()).hasSize(2);
        assertThat(message.guests().get(0).originPlace()).isNull();
        assertThat(message.guests().get(1).originPlace()).isEqualTo("Lima");
        assertThat(message.guests().get(1).destinationPlace()).isEqualTo("Cartagena");

        assertThat(result.stayId()).isEqualTo(stay.getValue().getId());
        assertThat(result.roomNumber()).isEqualTo("204");
        assertThat(result.roomStatus()).isEqualTo(RoomStatus.Occupied);
        assertThat(result.checkInDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("Un huésped inválido se rechaza antes de bloquear la habitación, sin escribir nada")
    void registerCheckIn_invalidGuest_writesNothing() {
        assertThatThrownBy(() -> service.registerCheckIn(command(List.of(
                        colombian("CC", "123", true),
                        new GuestInput("John", "Smith", "PAS", "X99", "Perú", BIRTH, null, "Cartagena", false)))))
                .isInstanceOf(InvalidGuestDataException.class);

        verifyNoInteractions(
                roomRepository, stayRepository, roomGuestRepository, transitionRoomStateUseCase, auditLog, outbox);
    }

    @Test
    @DisplayName("T021: si los ocupantes no coinciden con el guestCount no crea la estancia ni cambia el estado")
    void registerCheckIn_countMismatch_writesNothing() {
        givenEligibleReservation();

        assertThatThrownBy(() -> service.registerCheckIn(command(List.of(colombian("CC", "123", true)))))
                .isInstanceOf(GuestCountMismatchException.class);

        verify(roomRepository).lockById(ROOM_ID);
        verify(stayRepository).existsByReservationRefAndRoomId(REF, ROOM_ID);
        verifyNoMoreInteractions(stayRepository);
        verifyNoInteractions(roomGuestRepository, transitionRoomStateUseCase, auditLog, outbox);
    }

    private void givenEligibleReservation() {
        when(reservationRepository.findByReservationRef(REF))
                .thenReturn(Optional.of(new DailyReservation(
                        REF,
                        "Ana",
                        "Pérez",
                        "CC",
                        "123",
                        "Colombia",
                        "BOOKING",
                        TODAY,
                        TODAY.plusDays(2),
                        2,
                        OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                        List.of(new DailyReservationRoom(ROOM_ID, "204", "DOBLE", 2)))));
        when(roomRepository.lockById(ROOM_ID))
                .thenReturn(Optional.of(new Room(
                        ROOM_ID,
                        "204",
                        2,
                        RoomCategory.DOBLE,
                        3,
                        new BigDecimal("200000"),
                        RoomStatus.Reserved,
                        REF,
                        0)));
        when(stayRepository.existsByReservationRefAndRoomId(REF, ROOM_ID)).thenReturn(false);
    }

    private static RegisterCheckInCommand command(List<GuestInput> guests) {
        return new RegisterCheckInCommand(REF, ROOM_ID, guests, "lmendez");
    }

    private static GuestInput colombian(String documentType, String documentNumber, boolean reservationGuest) {
        return new GuestInput(
                "Ana", "Pérez", documentType, documentNumber, "Colombia", BIRTH, null, null, reservationGuest);
    }
}

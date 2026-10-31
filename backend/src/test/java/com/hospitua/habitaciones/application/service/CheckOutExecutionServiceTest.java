package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.CheckOutBillingEventPayload;
import com.hospitua.habitaciones.application.dto.CheckOutMessagePayload;
import com.hospitua.habitaciones.application.dto.CheckOutResultDto;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.RegisterCheckOutCommand;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.MandatoryConfirmationMissingException;
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
class CheckOutExecutionServiceTest {

    // 23:30 del 11-10 en Bogotá: en UTC ya es 12-10, así que la salida debe tomar la fecha de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 9);
    private static final String REF = "RSV-8D02E5A4";
    private static final UUID STAY_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String RECEPTIONIST = "aruiz";

    @Mock
    private StayRepositoryPort stayRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private RoomGuestRepositoryPort roomGuestRepository;

    @Mock
    private MarkPendingCleaningUseCase markPendingCleaningUseCase;

    @Mock
    private RoomAuditLogRepositoryPort auditLog;

    @Mock
    private OutboxEventPublisherPort outbox;

    private CheckOutExecutionService service;

    @BeforeEach
    void setUp() {
        service = new CheckOutExecutionService(
                stayRepository,
                roomRepository,
                roomGuestRepository,
                markPendingCleaningUseCase,
                auditLog,
                outbox,
                CLOCK);
    }

    @Test
    @DisplayName(
            "T005: bloquea la habitación, cierra la estancia, la libera a limpieza, audita y encola los dos mensajes")
    void registerCheckOut_closesStayReleasesRoomAndEnqueuesBothMessages() {
        when(stayRepository.findById(STAY_ID)).thenReturn(Optional.of(stay(null)));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room(RoomStatus.Occupied)));
        when(stayRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(roomGuestRepository.findByStayId(STAY_ID))
                .thenReturn(List.of(
                        guest("Ana", "CC", "123", "Colombia", null, null, true),
                        guest(
                                "John",
                                "PAS",
                                "X99",
                                "Estados Unidos",
                                "Miami, Estados Unidos",
                                "Cartagena, Colombia",
                                false)));

        CheckOutResultDto result = service.registerCheckOut(new RegisterCheckOutCommand(STAY_ID, true, RECEPTIONIST));

        assertThat(result).isEqualTo(new CheckOutResultDto(STAY_ID, ROOM_ID, "204", RoomStatus.PendingCleaning, TODAY));

        InOrder order = inOrder(roomRepository, stayRepository, markPendingCleaningUseCase, auditLog, outbox);
        order.verify(roomRepository).lockById(ROOM_ID);
        ArgumentCaptor<Stay> closed = ArgumentCaptor.forClass(Stay.class);
        order.verify(stayRepository).save(closed.capture());
        order.verify(markPendingCleaningUseCase)
                .markPendingCleaning(new MarkPendingCleaningCommand(ROOM_ID, SourceFlow.CHECK_OUT, RECEPTIONIST));
        ArgumentCaptor<RoomAuditEntry> audit = ArgumentCaptor.forClass(RoomAuditEntry.class);
        order.verify(auditLog).record(audit.capture());
        ArgumentCaptor<Object> departure = ArgumentCaptor.forClass(Object.class);
        order.verify(outbox).enqueue(eq("habitacion.checkout"), eq("CHECK_OUT"), departure.capture());
        ArgumentCaptor<Object> billing = ArgumentCaptor.forClass(Object.class);
        order.verify(outbox).enqueue(eq("facturacion.checkout"), eq("CHECK_OUT_BILLING"), billing.capture());

        assertThat(closed.getValue().getCheckOutDate()).isEqualTo(TODAY);
        assertThat(closed.getValue().getVersion()).isEqualTo(3);
        assertThat(audit.getValue().eventType()).isEqualTo(RoomAuditEventType.CHECK_OUT);
        assertThat(audit.getValue().actorId()).isEqualTo(RECEPTIONIST);
        assertThat(audit.getValue().details())
                .containsEntry("stayId", STAY_ID.toString())
                .containsEntry("reservationRef", REF)
                .containsEntry("checkOutDate", TODAY.toString());

        CheckOutMessagePayload message = (CheckOutMessagePayload) departure.getValue();
        assertThat(message.reservationRef()).isEqualTo(REF);
        assertThat(message.roomId()).isEqualTo(ROOM_ID);
        assertThat(message.movementType()).isEqualTo(MovementType.DEPARTURE);
        assertThat(message.movementDate()).isEqualTo(TODAY);
        assertThat(message.guests()).hasSize(2);
        assertThat(message.guests().get(0).birthDate()).isEqualTo(LocalDate.of(1990, 5, 12));
        assertThat(message.guests().get(0).originPlace()).isNull();
        assertThat(message.guests().get(1).originPlace()).isEqualTo("Miami, Estados Unidos");
        assertThat(message.guests().get(1).destinationPlace()).isEqualTo("Cartagena, Colombia");

        assertThat(billing.getValue())
                .isEqualTo(new CheckOutBillingEventPayload(
                        STAY_ID,
                        REF,
                        ROOM_ID,
                        RoomCategory.DOBLE,
                        CHECK_IN,
                        TODAY,
                        new CheckOutBillingEventPayload.BillingCustomer("Ana Pérez", "123")));
    }

    @Test
    @DisplayName("T015: sin la casilla de confirmación responde CONFIRMATION_REQUIRED sin leer ni escribir nada")
    void notConfirmed_isRejectedBeforeAnything() {
        assertThatThrownBy(() -> service.registerCheckOut(new RegisterCheckOutCommand(STAY_ID, false, RECEPTIONIST)))
                .isInstanceOf(MandatoryConfirmationMissingException.class);

        verifyNoInteractions(stayRepository, roomRepository, markPendingCleaningUseCase, auditLog, outbox);
    }

    @Test
    @DisplayName("T014: sin estancia activa responde ACTIVE_STAY_NOT_FOUND sin bloquear ni escribir")
    void noActiveStay_isNotFound() {
        when(stayRepository.findById(STAY_ID)).thenReturn(Optional.of(stay(TODAY)));

        assertThatThrownBy(() -> service.registerCheckOut(new RegisterCheckOutCommand(STAY_ID, true, RECEPTIONIST)))
                .isInstanceOf(ActiveStayNotFoundException.class);

        verifyNoInteractions(roomRepository, markPendingCleaningUseCase, auditLog, outbox);
    }

    @Test
    @DisplayName("T013: si la habitación bloqueada no está Ocupada responde ROOM_INVALID_STATE sin escribir")
    void roomNotOccupied_isRejectedWithoutWrites() {
        when(stayRepository.findById(STAY_ID)).thenReturn(Optional.of(stay(null)));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room(RoomStatus.PendingCleaning)));

        assertThatThrownBy(() -> service.registerCheckOut(new RegisterCheckOutCommand(STAY_ID, true, RECEPTIONIST)))
                .isInstanceOf(InvalidRoomTransitionException.class);

        verifyNoInteractions(markPendingCleaningUseCase, auditLog, outbox);
    }

    private static Stay stay(LocalDate checkOutDate) {
        return new Stay(
                STAY_ID, REF, ROOM_ID, "BOOKING", CHECK_IN, checkOutDate, CHECK_IN, TODAY, "Ana", "Pérez", "123", 3);
    }

    private static Room room(RoomStatus status) {
        return new Room(ROOM_ID, "204", 2, RoomCategory.DOBLE, 3, new BigDecimal("200000"), status, null, 0);
    }

    private static RoomGuest guest(
            String firstName,
            String documentType,
            String documentNumber,
            String nationality,
            String originPlace,
            String destinationPlace,
            boolean reservationGuest) {
        return new RoomGuest(
                UUID.randomUUID(),
                STAY_ID,
                firstName,
                "Pérez",
                documentType,
                documentNumber,
                nationality,
                LocalDate.of(1990, 5, 12),
                originPlace,
                destinationPlace,
                reservationGuest);
    }
}

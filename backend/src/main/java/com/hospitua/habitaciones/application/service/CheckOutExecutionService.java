package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.CheckOutBillingEventPayload;
import com.hospitua.habitaciones.application.dto.CheckOutMessagePayload;
import com.hospitua.habitaciones.application.dto.CheckOutResultDto;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.RegisterCheckOutCommand;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterCheckOutUseCase;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException;
import com.hospitua.habitaciones.domain.exception.MandatoryConfirmationMissingException;
import com.hospitua.habitaciones.domain.model.CheckOutEligibility;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.Stay;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro del Check-Out (plan-registrar-check-out.md T010). Bloquea la fila de la habitación (regla 13 del plan base)
 * antes de repetir las reglas del Paso 1: de dos Check-Out simultáneos de la misma estancia, el segundo ve la
 * habitación ya en {@code PendingCleaning} y se rechaza con 409. No llama a Módulo 2 ni a Módulo 3: los dos mensajes se
 * encolan en la misma transacción (FR-014).
 */
@Service
@RequiredArgsConstructor
public class CheckOutExecutionService implements RegisterCheckOutUseCase {

    public static final String ROUTING_KEY = "habitacion.checkout";
    public static final String EVENT_TYPE = "CHECK_OUT";
    public static final String BILLING_ROUTING_KEY = "facturacion.checkout";
    public static final String BILLING_EVENT_TYPE = "CHECK_OUT_BILLING";

    private final StayRepositoryPort stayRepository;
    private final RoomRepositoryPort roomRepository;
    private final RoomGuestRepositoryPort roomGuestRepository;
    private final MarkPendingCleaningUseCase markPendingCleaningUseCase;
    private final RoomAuditLogRepositoryPort auditLog;
    private final OutboxEventPublisherPort outbox;
    private final Clock clock;

    @Override
    @Transactional
    public CheckOutResultDto registerCheckOut(RegisterCheckOutCommand command) {
        if (!command.confirmed()) {
            throw new MandatoryConfirmationMissingException();
        }
        Stay stay = stayRepository
                .findById(command.stayId())
                .filter(Stay::isActive)
                .orElseThrow(ActiveStayNotFoundException::new);
        Room room = roomRepository.lockById(stay.getRoomId()).orElse(null);
        CheckOutEligibility.verify(stay.getReservationRef(), stay.getRoomId(), stay, room);

        LocalDate today = LocalDate.now(clock);
        Stay closedStay = stayRepository.save(stay.checkOut(today));
        markPendingCleaningUseCase.markPendingCleaning(
                new MarkPendingCleaningCommand(room.getId(), SourceFlow.CHECK_OUT, command.receptionistId()));
        auditLog.record(new RoomAuditEntry(
                room.getId(),
                RoomAuditEventType.CHECK_OUT,
                command.receptionistId(),
                Map.of(
                        "stayId", closedStay.getId().toString(),
                        "reservationRef", closedStay.getReservationRef(),
                        "checkOutDate", today.toString())));
        outbox.enqueue(
                ROUTING_KEY,
                EVENT_TYPE,
                CheckOutMessagePayload.departure(
                        closedStay.getReservationRef(),
                        room.getId(),
                        today,
                        roomGuestRepository.findByStayId(closedStay.getId())));
        outbox.enqueue(
                BILLING_ROUTING_KEY,
                BILLING_EVENT_TYPE,
                CheckOutBillingEventPayload.of(closedStay, room.getCategoryRoom()));

        return new CheckOutResultDto(
                closedStay.getId(), room.getId(), room.getRoomNumber(), RoomStatus.PendingCleaning, today);
    }
}

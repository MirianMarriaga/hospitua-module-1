package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.CheckInMessagePayload;
import com.hospitua.habitaciones.application.dto.CheckInResultDto;
import com.hospitua.habitaciones.application.dto.RegisterCheckInCommand;
import com.hospitua.habitaciones.application.dto.RegisterCheckInCommand.GuestInput;
import com.hospitua.habitaciones.application.ports.in.RegisterCheckInUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.CheckInEligibility;
import com.hospitua.habitaciones.domain.model.CheckInGuest;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.Stay;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro del Check-In (plan-registrar-check-in.md T016). Bloquea la fila de la habitación (regla 13 del plan base)
 * antes de repetir las reglas del Paso 1, así dos Check-In simultáneos de la misma habitación se serializan y el
 * segundo ve la estancia del primero. La notificación a Módulo 2 se encola en la misma transacción (FR-014).
 */
@Service
@RequiredArgsConstructor
public class CheckInExecutionService implements RegisterCheckInUseCase {

    public static final String ROUTING_KEY = "habitacion.checkin";
    public static final String EVENT_TYPE = "CHECK_IN";

    private final DailyReservationRepositoryPort reservationRepository;
    private final RoomRepositoryPort roomRepository;
    private final StayRepositoryPort stayRepository;
    private final RoomGuestRepositoryPort roomGuestRepository;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;
    private final RoomAuditLogRepositoryPort auditLog;
    private final OutboxEventPublisherPort outbox;
    private final Clock clock;

    @Override
    @Transactional
    public CheckInResultDto registerCheckIn(RegisterCheckInCommand command) {
        LocalDate today = LocalDate.now(clock);
        List<CheckInGuest> guests = validatedGuests(command.guests(), today);
        Room room = roomRepository.lockById(command.roomId()).orElse(null);
        CheckInEligibility eligibility = CheckInEligibility.verify(
                command.reservationRef(),
                command.roomId(),
                reservationRepository
                        .findByReservationRef(command.reservationRef())
                        .orElse(null),
                room,
                stayRepository.existsByReservationRefAndRoomId(command.reservationRef(), command.roomId()),
                today);
        eligibility.verifyGuests(guests);

        DailyReservation reservation = eligibility.reservation();
        Stay stay = stayRepository.save(new Stay(
                UUID.randomUUID(),
                reservation.reservationRef(),
                command.roomId(),
                reservation.source(),
                today,
                null,
                reservation.startDate(),
                reservation.endDate(),
                reservation.guestFirstName(),
                reservation.guestLastName(),
                reservation.guestDocumentNumber(),
                0));
        List<RoomGuest> roomGuests =
                guests.stream().map(guest -> toRoomGuest(stay.getId(), guest)).toList();
        roomGuestRepository.saveAll(roomGuests);
        transitionRoomStateUseCase.transition(
                command.roomId(),
                RoomStatus.Occupied,
                command.receptionistId(),
                SourceFlow.CHECK_IN,
                reservation.reservationRef());
        auditLog.record(new RoomAuditEntry(
                command.roomId(),
                RoomAuditEventType.CHECK_IN,
                command.receptionistId(),
                Map.of("stayId", stay.getId().toString(), "reservationRef", reservation.reservationRef())));
        outbox.enqueue(
                ROUTING_KEY,
                EVENT_TYPE,
                CheckInMessagePayload.entry(reservation.reservationRef(), command.roomId(), today, roomGuests));

        return new CheckInResultDto(
                stay.getId(),
                reservation.reservationRef(),
                command.roomId(),
                eligibility.room().getRoomNumber(),
                RoomStatus.Occupied,
                today);
    }

    private static List<CheckInGuest> validatedGuests(List<GuestInput> inputs, LocalDate today) {
        return IntStream.range(0, inputs.size())
                .mapToObj(i -> {
                    GuestInput input = inputs.get(i);
                    return CheckInGuest.of(
                            i,
                            input.firstName(),
                            input.lastName(),
                            input.documentType(),
                            input.documentNumber(),
                            input.nationality(),
                            input.birthDate(),
                            input.originPlace(),
                            input.destinationPlace(),
                            input.reservationGuest(),
                            today);
                })
                .toList();
    }

    private static RoomGuest toRoomGuest(UUID stayId, CheckInGuest guest) {
        return new RoomGuest(
                UUID.randomUUID(),
                stayId,
                guest.firstName(),
                guest.lastName(),
                guest.documentType(),
                guest.documentNumber(),
                guest.nationality(),
                guest.birthDate(),
                guest.originPlace(),
                guest.destinationPlace(),
                guest.reservationGuest());
    }
}

package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.DailyReservationListMessageDto;
import com.hospitua.habitaciones.application.dto.DailyReservationUpdateMessageDto;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.event.DailyReservationListIngestedEvent;
import com.hospitua.habitaciones.application.ports.in.IngestDailyReservationsUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationIngestionOutcome;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageType;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingesta de la cola diaria de reservas (consultar-reservas T012 y T013). No transiciona habitaciones por su cuenta:
 * aparta con {@link MarkRoomReservedUseCase} y libera con {@link MarkRoomAvailableUseCase}, bloqueando antes la fila
 * de la habitación (regla 13). "Hoy" es el {@code operationalDate} del mensaje, el día con el que Módulo 2 lo generó.
 */
@Service
@RequiredArgsConstructor
public class DailyReservationIngestionService implements IngestDailyReservationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DailyReservationIngestionService.class);

    private final DailyReservationRepositoryPort reservationRepository;
    private final RoomRepositoryPort roomRepository;
    private final MarkRoomReservedUseCase markRoomReserved;
    private final MarkRoomAvailableUseCase markRoomAvailable;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public DailyReservationIngestionOutcome ingestDailyList(DailyReservationListMessageDto message) {
        if (reservationRepository.isMessageLogged(message.messageId())) {
            return DailyReservationIngestionOutcome.DUPLICATE;
        }
        LocalDate day = message.operationalDate();
        if (isBeforeLocalCopyDay(day)) {
            logMessage(
                    message.messageId(), message.sequenceNumber(), day, DailyReservationMessageType.DAILY_LIST, null);
            return DailyReservationIngestionOutcome.OUTDATED_DAY;
        }
        warnIfGap(message.messageId(), 1, message.sequenceNumber());

        // Primero las liberaciones: habitaciones de la copia anterior que no siguen en la misma reserva
        Map<String, DailyReservation> incoming = message.reservations().stream()
                .collect(Collectors.toMap(
                        DailyReservation::reservationRef, Function.identity(), (a, b) -> b, LinkedHashMap::new));
        for (DailyReservation previous : reservationRepository.findAll()) {
            Set<UUID> kept = roomIds(incoming.get(previous.reservationRef()));
            releaseRoomsNotIn(previous, kept);
        }

        reservationRepository.purge();
        incoming.values().forEach(reservationRepository::save);
        for (DailyReservation reservation : incoming.values()) {
            reserveIfArrivingOn(reservation, day, ReservationTrigger.DAILY_LIST);
        }

        logMessage(message.messageId(), message.sequenceNumber(), day, DailyReservationMessageType.DAILY_LIST, null);
        eventPublisher.publishEvent(new DailyReservationListIngestedEvent(day));
        return DailyReservationIngestionOutcome.APPLIED;
    }

    @Override
    @Transactional
    public DailyReservationIngestionOutcome ingestUpdate(DailyReservationUpdateMessageDto message) {
        if (reservationRepository.isMessageLogged(message.messageId())) {
            return DailyReservationIngestionOutcome.DUPLICATE;
        }
        LocalDate day = message.operationalDate();
        String reservationRef = message.reservationRef();
        if (isBeforeLocalCopyDay(day)) {
            logUpdate(message);
            return DailyReservationIngestionOutcome.OUTDATED_DAY;
        }
        OptionalLong lastForReservation = reservationRepository.findLastSequenceForReservation(day, reservationRef);
        if (lastForReservation.isPresent() && message.sequenceNumber() <= lastForReservation.getAsLong()) {
            logUpdate(message);
            return DailyReservationIngestionOutcome.OUTDATED_SEQUENCE;
        }
        OptionalLong lastOfDay = reservationRepository.findLastSequence(day);
        warnIfGap(message.messageId(), lastOfDay.orElse(0) + 1, message.sequenceNumber());

        Optional<DailyReservation> previous = reservationRepository.findByReservationRef(reservationRef);
        switch (message.updateType()) {
            case ADDED, UPDATED -> {
                DailyReservation reservation = message.reservation();
                Set<UUID> reserved = reservation.startDate().equals(day) ? roomIds(reservation) : Set.of();
                previous.ifPresent(old -> releaseRoomsNotIn(old, reserved));
                reservationRepository.save(reservation);
                reserveIfArrivingOn(reservation, day, ReservationTrigger.ADDED_UPDATE);
            }
            case REMOVED -> {
                reservationRepository.deleteByReservationRef(reservationRef);
                previous.ifPresent(old -> releaseRoomsNotIn(old, Set.of()));
            }
        }

        logUpdate(message);
        return DailyReservationIngestionOutcome.APPLIED;
    }

    private boolean isBeforeLocalCopyDay(LocalDate operationalDate) {
        return reservationRepository
                .findLatestDailyListDate()
                .map(operationalDate::isBefore)
                .orElse(false);
    }

    private void warnIfGap(String messageId, long expected, long received) {
        if (received > expected) {
            log.warn(
                    "[SEQUENCE_GAP] Mensaje {} de la cola diaria con sequenceNumber {}; se esperaba {}. Se aplica igual.",
                    messageId,
                    received,
                    expected);
        }
    }

    private void releaseRoomsNotIn(DailyReservation reservation, Set<UUID> kept) {
        for (DailyReservationRoom room : reservation.rooms()) {
            if (!kept.contains(room.roomId())) {
                releaseIfReservedBy(room.roomId(), reservation.reservationRef());
            }
        }
    }

    /** Libera la habitación solo si sigue {@code Reserved} por esa reserva; si no, solo cambia la copia local. */
    private void releaseIfReservedBy(UUID roomId, String reservationRef) {
        Optional<Room> room = roomRepository.lockById(roomId);
        if (room.isPresent()
                && room.get().getStatus() == RoomStatus.Reserved
                && Objects.equals(room.get().getReservedByReservationRef(), reservationRef)) {
            markRoomAvailable.markAvailable(
                    new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, null, reservationRef));
        }
    }

    /** Aparta cada habitación si la reserva llega ese día; un conflicto o una habitación desconocida no detienen. */
    private void reserveIfArrivingOn(DailyReservation reservation, LocalDate day, ReservationTrigger trigger) {
        if (!reservation.startDate().equals(day)) {
            return;
        }
        for (DailyReservationRoom room : reservation.rooms()) {
            roomRepository.lockById(room.roomId());
            markRoomReserved.markReserved(
                    new MarkRoomReservedCommand(room.roomId(), reservation.reservationRef(), trigger));
        }
    }

    private static Set<UUID> roomIds(DailyReservation reservation) {
        return reservation == null
                ? Set.of()
                : reservation.rooms().stream().map(DailyReservationRoom::roomId).collect(Collectors.toSet());
    }

    private void logUpdate(DailyReservationUpdateMessageDto message) {
        logMessage(
                message.messageId(),
                message.sequenceNumber(),
                message.operationalDate(),
                DailyReservationMessageType.UPDATE,
                message.reservationRef());
    }

    private void logMessage(
            String messageId,
            long sequenceNumber,
            LocalDate operationalDate,
            DailyReservationMessageType type,
            String reservationRef) {
        reservationRepository.logMessage(new DailyReservationMessageLog(
                messageId, sequenceNumber, operationalDate, type, reservationRef, OffsetDateTime.now(clock)));
    }
}

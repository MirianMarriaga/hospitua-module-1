package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.PendingArrivalsDto;
import com.hospitua.habitaciones.application.ports.in.SearchDailyReservationsUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.ArrivalGroup;
import com.hospitua.habitaciones.domain.model.ArrivalItemSummary;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.PendingArrival;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pestaña Llegadas desde la copia local, sin llamar a Módulo 2 (plan-consultar-reservas.md T019, T020, T030;
 * plan-consultar-panel-recepcion.md T011). Cada habitación pendiente es una fila con el estado actual de la habitación
 * y su aviso; una habitación que no está en el inventario se omite, como en la ingesta. {@code REPEATABLE_READ} lee la
 * copia local y las habitaciones en una misma instantánea.
 */
@Service
@RequiredArgsConstructor
public class DailyReservationSearchService implements SearchDailyReservationsUseCase {

    private final DailyReservationRepositoryPort reservationRepository;
    private final RoomRepositoryPort roomRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PendingArrivalsDto searchPendingArrivals(String query) {
        LocalDate today = LocalDate.now(clock);
        if (!reservationRepository.isDailyListReceived(today)) {
            return PendingArrivalsDto.listNotReceived();
        }
        List<PendingArrival> arrivals =
                reservationRepository.searchPendingArrivals(today, SearchQuery.normalize(query));
        List<UUID> pendingRoomIds = arrivals.stream()
                .flatMap(arrival -> arrival.pendingRoomIds().stream())
                .toList();
        Map<UUID, RoomStatus> statuses = roomRepository.findAllById(pendingRoomIds).stream()
                .collect(Collectors.toMap(Room::getId, Room::getStatus));
        List<ArrivalGroup> groups = arrivals.stream()
                .map(arrival -> toGroup(arrival, statuses))
                .filter(group -> group.pendingRooms() > 0)
                .toList();
        return new PendingArrivalsDto(true, groups);
    }

    private static ArrivalGroup toGroup(PendingArrival arrival, Map<UUID, RoomStatus> statuses) {
        DailyReservation reservation = arrival.reservation();
        List<ArrivalItemSummary> rows = reservation.rooms().stream()
                .filter(room -> arrival.isPending(room.roomId()) && statuses.containsKey(room.roomId()))
                .map(room -> ArrivalItemSummary.of(reservation, room, statuses.get(room.roomId())))
                .toList();
        return new ArrivalGroup(
                reservation.reservationRef(), rows.size(), reservation.rooms().size(), rows);
    }
}

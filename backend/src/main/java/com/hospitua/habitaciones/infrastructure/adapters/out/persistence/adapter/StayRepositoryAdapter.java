package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.DepartureSummary;
import com.hospitua.habitaciones.domain.model.Stay;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.StayJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.StayMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.DepartureRow;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataStayJpaRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia para estancias implementando StayRepositoryPort.
 */
@Component
@RequiredArgsConstructor
public class StayRepositoryAdapter implements StayRepositoryPort {

    /** Primero las vencidas (la salida esperada más antigua) y después el orden natural del número de habitación. */
    private static final Comparator<DepartureSummary> DEPARTURE_ORDER = Comparator.comparing(
                    DepartureSummary::expectedCheckoutDate)
            .thenComparing(DepartureSummary::roomNumber, RoomNumberOrder.NUMBERS);

    private final SpringDataStayJpaRepository stayJpaRepository;
    private final StayMapper stayMapper;

    @Override
    public Stay save(Stay stay) {
        StayJpaEntity entity = stayMapper.toEntity(stay);
        StayJpaEntity saved = stayJpaRepository.save(entity);
        return stayMapper.toDomain(saved);
    }

    @Override
    public Optional<Stay> findById(UUID id) {
        return stayJpaRepository.findById(id).map(stayMapper::toDomain);
    }

    @Override
    public boolean existsByReservationRefAndRoomId(String reservationRef, UUID roomId) {
        return stayJpaRepository.existsByReservationRefAndRoomId(reservationRef, roomId);
    }

    @Override
    public List<DepartureSummary> searchActiveDepartures(LocalDate today, String normalizedQuery) {
        // Una sola consulta: la estancia, su habitación y el conteo de huéspedes
        List<DepartureRow> rows = normalizedQuery == null
                ? stayJpaRepository.findActiveDepartures(today)
                : stayJpaRepository.searchActiveDepartures(
                        today, normalizedQuery, LikePatterns.containing(normalizedQuery));
        return rows.stream()
                .map(row -> DepartureSummary.of(
                        stayMapper.toDomain(row.stay()),
                        row.roomNumber(),
                        row.categoryRoom(),
                        row.guestCount().intValue(),
                        today))
                .sorted(DEPARTURE_ORDER)
                .toList();
    }

    @Override
    public long countActiveWithExpectedCheckoutOn(LocalDate date) {
        return stayJpaRepository.countByCheckOutDateIsNullAndExpectedCheckoutDate(date);
    }

    @Override
    public long countActiveWithExpectedCheckoutBefore(LocalDate date) {
        return stayJpaRepository.countByCheckOutDateIsNullAndExpectedCheckoutDateBefore(date);
    }
}

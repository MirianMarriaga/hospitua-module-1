package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageType;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationRoomJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.DailyReservationMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataDailyReservationJpaRepository;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataDailyReservationMessageLogJpaRepository;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataDailyReservationRoomJpaRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia de la copia local de reservas implementando DailyReservationRepositoryPort
 * (plan-consultar-reservas.md T011). Las escrituras de varias tablas son atómicas por sí solas y se unen a la
 * transacción de la ingesta cuando la hay.
 */
@Component
@RequiredArgsConstructor
public class DailyReservationRepositoryAdapter implements DailyReservationRepositoryPort {

    private final SpringDataDailyReservationJpaRepository reservationRepository;
    private final SpringDataDailyReservationRoomJpaRepository roomRepository;
    private final SpringDataDailyReservationMessageLogJpaRepository messageLogRepository;
    private final DailyReservationMapper mapper;

    @Override
    @Transactional
    public void save(DailyReservation reservation) {
        roomRepository.deleteByReservationRef(reservation.reservationRef());
        reservationRepository.save(mapper.toEntity(reservation));
        roomRepository.saveAll(mapper.toRoomEntities(reservation));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DailyReservation> findByReservationRef(String reservationRef) {
        return reservationRepository
                .findById(reservationRef)
                .map(entity ->
                        mapper.toDomain(entity, roomRepository.findByReservationRefOrderByRoomNumber(reservationRef)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyReservation> findAll() {
        // Dos consultas en total: las reservas y todas sus habitaciones agrupadas
        Map<String, List<DailyReservationRoomJpaEntity>> roomsByReservation = roomRepository.findAll().stream()
                .sorted(Comparator.comparing(DailyReservationRoomJpaEntity::getRoomNumber))
                .collect(Collectors.groupingBy(DailyReservationRoomJpaEntity::getReservationRef));
        return reservationRepository.findAll().stream()
                .sorted(Comparator.comparing(entity -> entity.getReservationRef()))
                .map(entity ->
                        mapper.toDomain(entity, roomsByReservation.getOrDefault(entity.getReservationRef(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public void deleteByReservationRef(String reservationRef) {
        roomRepository.deleteByReservationRef(reservationRef);
        reservationRepository.deleteById(reservationRef);
    }

    @Override
    @Transactional
    public void purge() {
        roomRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
    }

    @Override
    public boolean isMessageLogged(String messageId) {
        return messageLogRepository.existsById(messageId);
    }

    @Override
    public void logMessage(DailyReservationMessageLog entry) {
        messageLogRepository.save(mapper.toEntity(entry));
    }

    @Override
    public Optional<LocalDate> findLatestDailyListDate() {
        return messageLogRepository.findLatestOperationalDate(
                DailyReservationMapper.toMessageTypeColumn(DailyReservationMessageType.DAILY_LIST));
    }

    @Override
    public OptionalLong findLastSequence(LocalDate operationalDate) {
        return toOptionalLong(messageLogRepository.findLastSequence(operationalDate));
    }

    @Override
    public OptionalLong findLastSequenceForReservation(LocalDate operationalDate, String reservationRef) {
        return toOptionalLong(messageLogRepository.findLastSequenceForReservation(operationalDate, reservationRef));
    }

    @Override
    public Optional<String> findPendingArrivalReservationRef(UUID roomId, LocalDate operationalDate) {
        return reservationRepository.findPendingArrivalReservationRef(roomId, operationalDate);
    }

    private static OptionalLong toOptionalLong(Optional<Long> value) {
        return value.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }
}

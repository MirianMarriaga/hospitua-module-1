package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.TechnicalBlockReportMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataTechnicalBlockReportJpaRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia de los informes de bloqueo técnico (plan-programar-bloqueo-tecnico-para-habitacion.md T003).
 * Guarda con {@code saveAndFlush} para que una falla de la base (p. ej. el índice único de un solo {@code Applied} por
 * habitación) salga del caso de uso y no al confirmar.
 */
@Component
@RequiredArgsConstructor
public class TechnicalBlockReportRepositoryAdapter implements TechnicalBlockReportRepositoryPort {

    private final SpringDataTechnicalBlockReportJpaRepository reportJpaRepository;
    private final TechnicalBlockReportMapper reportMapper;

    @Override
    public TechnicalBlockReport save(TechnicalBlockReport report) {
        return reportMapper.toDomain(reportJpaRepository.saveAndFlush(reportMapper.toEntity(report)));
    }

    @Override
    public List<TechnicalBlockReport> findActiveOverlapping(UUID roomId, LocalDate from, LocalDate to) {
        return reportJpaRepository.findOverlapping(roomId, TechnicalBlockStatus.ACTIVE, from, to).stream()
                .map(reportMapper::toDomain)
                .toList();
    }

    /** El índice único parcial garantiza a lo sumo un {@code Applied} por habitación. */
    @Override
    public Map<UUID, TechnicalBlockReport> findAppliedByRoomIds(Collection<UUID> roomIds) {
        return firstByRoom(roomIds, TechnicalBlockStatus.Applied);
    }

    @Override
    public Map<UUID, TechnicalBlockReport> findEarliestScheduledByRoomIds(Collection<UUID> roomIds) {
        return firstByRoom(roomIds, TechnicalBlockStatus.Scheduled);
    }

    @Override
    public List<UUID> findRoomIdsWithScheduledCovering(LocalDate today) {
        return reportJpaRepository.findRoomIdsCovering(TechnicalBlockStatus.Scheduled, today);
    }

    @Override
    @Transactional
    public int expireScheduledEndedBefore(LocalDate today) {
        return reportJpaRepository.expireEndedBefore(
                TechnicalBlockStatus.Scheduled, TechnicalBlockStatus.Expired, today);
    }

    /** Una sola consulta ordenada por inicio; de cada habitación queda el primero. */
    private Map<UUID, TechnicalBlockReport> firstByRoom(Collection<UUID> roomIds, TechnicalBlockStatus status) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TechnicalBlockReport> byRoom = new LinkedHashMap<>();
        reportJpaRepository.findByRoomIdInAndStatusOrderByTechnicalBlockStartDateAsc(roomIds, status).stream()
                .map(reportMapper::toDomain)
                .forEach(report -> byRoom.putIfAbsent(report.getRoomId(), report));
        return byRoom;
    }
}

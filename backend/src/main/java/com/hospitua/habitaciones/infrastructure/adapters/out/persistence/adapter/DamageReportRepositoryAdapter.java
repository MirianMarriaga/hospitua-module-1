package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.DamageReportMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataDamageReportJpaRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia de los reportes de daño (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T002).
 * Guarda con {@code saveAndFlush} para que una falla de la base salga del caso de uso, antes de encolar el aviso, y no
 * al confirmar la transacción.
 */
@Component
@RequiredArgsConstructor
public class DamageReportRepositoryAdapter implements DamageReportRepositoryPort {

    private final SpringDataDamageReportJpaRepository damageReportJpaRepository;
    private final DamageReportMapper damageReportMapper;

    @Override
    public DamageReport save(DamageReport report) {
        return damageReportMapper.toDomain(damageReportJpaRepository.saveAndFlush(damageReportMapper.toEntity(report)));
    }

    @Override
    public Optional<DamageReport> findLatestByRoom(UUID roomId) {
        return damageReportJpaRepository
                .findFirstByRoomIdOrderByReportDateTimeDesc(roomId)
                .map(damageReportMapper::toDomain);
    }

    /** Si dos reportes de una habitación tuvieran la misma marca, queda uno de ellos. */
    @Override
    public Map<UUID, DamageReport> findLatestByRoomIds(Collection<UUID> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        return damageReportJpaRepository.findLatestByRoomIds(roomIds).stream()
                .map(damageReportMapper::toDomain)
                .collect(Collectors.toMap(DamageReport::getRoomId, report -> report, (first, second) -> first));
    }
}

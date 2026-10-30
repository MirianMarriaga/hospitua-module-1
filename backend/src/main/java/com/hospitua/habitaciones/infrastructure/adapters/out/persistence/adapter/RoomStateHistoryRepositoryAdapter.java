package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.application.ports.out.RoomStateHistoryRepositoryPort;
import com.hospitua.habitaciones.domain.model.RoomStateHistory;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomStateHistoryJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.RoomStateHistoryMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.RoomStateHistoryQueryRepository;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataRoomStateHistoryJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia para el historial de estados implementando RoomStateHistoryRepositoryPort.
 */
@Component
@RequiredArgsConstructor
public class RoomStateHistoryRepositoryAdapter implements RoomStateHistoryRepositoryPort {

    private final SpringDataRoomStateHistoryJpaRepository repository;
    private final RoomStateHistoryMapper mapper;
    private final RoomStateHistoryQueryRepository queryRepository;

    @Override
    public RoomStateHistory save(RoomStateHistory history) {
        RoomStateHistoryJpaEntity entity = mapper.toEntity(history);
        RoomStateHistoryJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RoomStateHistory> findOpenByRoomId(UUID roomId) {
        return repository.findByRoomIdAndEndDateTimeIsNull(roomId).map(mapper::toDomain);
    }

    @Override
    public PagedResultDto<RoomStateHistoryRowDto> search(RoomStateHistoryFilter filter, int page) {
        return queryRepository.search(filter, page, PAGE_SIZE);
    }
}

package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.Stay;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.StayJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.StayMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataStayJpaRepository;
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
}

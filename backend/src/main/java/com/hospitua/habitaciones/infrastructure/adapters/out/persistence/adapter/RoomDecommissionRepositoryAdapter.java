package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.RoomDecommissionMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataRoomDecommissionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia de las bajas (plan-dar-de-baja-habitacion.md T003). Guarda con {@code saveAndFlush} para
 * que una falla de la base salga del caso de uso y revierta la transición (caso borde 3), no al confirmar.
 */
@Component
@RequiredArgsConstructor
public class RoomDecommissionRepositoryAdapter implements RoomDecommissionRepositoryPort {

    private final SpringDataRoomDecommissionJpaRepository decommissionJpaRepository;
    private final RoomDecommissionMapper decommissionMapper;

    @Override
    public RoomDecommission save(RoomDecommission decommission) {
        return decommissionMapper.toDomain(
                decommissionJpaRepository.saveAndFlush(decommissionMapper.toEntity(decommission)));
    }
}

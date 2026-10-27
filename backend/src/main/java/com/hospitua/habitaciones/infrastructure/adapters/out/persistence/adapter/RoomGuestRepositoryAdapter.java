package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomGuestJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.RoomGuestMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataRoomGuestJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia para ocupantes físicos implementando RoomGuestRepositoryPort.
 * Solo implementa saveAll y findByStayId conforme a la inmutabilidad de la regla 4.
 */
@Component
@RequiredArgsConstructor
public class RoomGuestRepositoryAdapter implements RoomGuestRepositoryPort {

    private final SpringDataRoomGuestJpaRepository roomGuestJpaRepository;
    private final RoomGuestMapper roomGuestMapper;

    @Override
    public List<RoomGuest> saveAll(List<RoomGuest> guests) {
        List<RoomGuestJpaEntity> entities =
                guests.stream().map(roomGuestMapper::toEntity).toList();
        List<RoomGuestJpaEntity> saved = roomGuestJpaRepository.saveAll(entities);
        return saved.stream().map(roomGuestMapper::toDomain).toList();
    }

    @Override
    public List<RoomGuest> findByStayId(UUID stayId) {
        List<RoomGuestJpaEntity> entities = roomGuestJpaRepository.findByStayId(stayId);
        return entities.stream().map(roomGuestMapper::toDomain).toList();
    }
}

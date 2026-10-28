package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.RoomMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataRoomJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia para habitaciones implementando RoomRepositoryPort.
 */
@Component
@RequiredArgsConstructor
public class RoomRepositoryAdapter implements RoomRepositoryPort {

    private final SpringDataRoomJpaRepository roomJpaRepository;
    private final RoomMapper roomMapper;

    @Override
    public Optional<Room> findById(UUID roomId) {
        return roomJpaRepository.findById(roomId).map(roomMapper::toDomain);
    }

    @Override
    public Room save(Room room) {
        RoomJpaEntity entity = roomMapper.toEntity(room);
        RoomJpaEntity saved = roomJpaRepository.save(entity);
        return roomMapper.toDomain(saved);
    }

    /** Solo dentro de la transacción del invocador: fuera de ella el bloqueo se liberaría al instante (regla 13). */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public Optional<Room> lockById(UUID roomId) {
        return roomJpaRepository.findByIdWithPessimisticLock(roomId).map(roomMapper::toDomain);
    }

    @Override
    public List<Room> findByStatusIn(Set<RoomStatus> statuses) {
        List<RoomJpaEntity> entities = roomJpaRepository.findByStatusIn(statuses);
        return entities.stream()
                .map(roomMapper::toDomain)
                .sorted(RoomNumberOrder.NATURAL)
                .toList();
    }

    @Override
    public List<Room> findByStatusNot(RoomStatus status) {
        List<RoomJpaEntity> entities = roomJpaRepository.findByStatusNot(status);
        return entities.stream()
                .map(roomMapper::toDomain)
                .sorted(RoomNumberOrder.NATURAL)
                .toList();
    }
}

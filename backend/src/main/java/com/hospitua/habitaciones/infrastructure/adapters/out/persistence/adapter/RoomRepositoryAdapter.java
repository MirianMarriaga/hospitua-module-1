package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.RoomMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataRoomJpaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia para habitaciones implementando RoomRepositoryPort.
 */
@Component
@RequiredArgsConstructor
public class RoomRepositoryAdapter implements RoomRepositoryPort {

    private static final Comparator<Room> NATURAL_ROOM_ORDER = Comparator.comparingInt(
                    (Room r) -> extractNumericPart(r.getRoomNumber()))
            .thenComparing(r -> extractSuffixPart(r.getRoomNumber()));

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

    @org.springframework.transaction.annotation.Transactional
    @Override
    public Optional<Room> lockById(UUID roomId) {
        return roomJpaRepository.findByIdWithPessimisticLock(roomId).map(roomMapper::toDomain);
    }

    @Override
    public List<Room> findByStatusIn(Set<RoomStatus> statuses) {
        List<RoomJpaEntity> entities = roomJpaRepository.findByStatusIn(statuses);
        return entities.stream()
                .map(roomMapper::toDomain)
                .sorted(NATURAL_ROOM_ORDER)
                .toList();
    }

    @Override
    public List<Room> findByStatusNot(RoomStatus status) {
        List<RoomJpaEntity> entities = roomJpaRepository.findByStatusNot(status);
        return entities.stream()
                .map(roomMapper::toDomain)
                .sorted(NATURAL_ROOM_ORDER)
                .toList();
    }

    private static int extractNumericPart(String roomNumber) {
        if (roomNumber == null) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roomNumber.length(); i++) {
            char c = roomNumber.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            } else {
                break;
            }
        }
        if (sb.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractSuffixPart(String roomNumber) {
        if (roomNumber == null) {
            return "";
        }
        int i = 0;
        while (i < roomNumber.length() && Character.isDigit(roomNumber.charAt(i))) {
            i++;
        }
        return roomNumber.substring(i);
    }
}

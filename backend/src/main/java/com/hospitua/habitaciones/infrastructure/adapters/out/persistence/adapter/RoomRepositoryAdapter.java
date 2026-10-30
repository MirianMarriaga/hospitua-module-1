package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.RoomMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.RoomInventorySpecifications;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataRoomJpaRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia para habitaciones implementando RoomRepositoryPort.
 */
@Component
@RequiredArgsConstructor
public class RoomRepositoryAdapter implements RoomRepositoryPort {

    private static final String ROOM_NUMBER_UNIQUE = "uk_room_room_number";

    private final SpringDataRoomJpaRepository roomJpaRepository;
    private final RoomMapper roomMapper;

    @Override
    public Optional<Room> findById(UUID roomId) {
        return roomJpaRepository.findById(roomId).map(roomMapper::toDomain);
    }

    @Override
    public List<Room> findAllById(Collection<UUID> roomIds) {
        return roomJpaRepository.findAllById(roomIds).stream()
                .map(roomMapper::toDomain)
                .toList();
    }

    @Override
    public Room save(Room room) {
        RoomJpaEntity entity = roomMapper.toEntity(room);
        RoomJpaEntity saved = roomJpaRepository.save(entity);
        return roomMapper.toDomain(saved);
    }

    @Override
    public Room insert(Room room) {
        try {
            return roomMapper.toDomain(roomJpaRepository.saveAndFlush(roomMapper.toEntity(room)));
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, ROOM_NUMBER_UNIQUE)) {
                throw new RoomNumberAlreadyExistsException(room.getRoomNumber());
            }
            throw ex;
        }
    }

    @Override
    public boolean existsByRoomNumber(String roomNumber) {
        return roomJpaRepository.existsByRoomNumber(roomNumber);
    }

    @Override
    public PagedResultDto<Room> searchInventory(RoomInventoryFilter filter, int page, int size) {
        List<Room> rooms = roomJpaRepository.findAll(RoomInventorySpecifications.matching(filter)).stream()
                .map(roomMapper::toDomain)
                .sorted(inventoryOrder(filter))
                .toList();
        return PagedResultDto.of(rooms, page, size);
    }

    @Override
    public List<Room> findSellableByCategory(RoomCategory categoryRoom) {
        return roomJpaRepository.findByCategoryRoomAndStatusNot(categoryRoom, RoomStatus.Inactive).stream()
                .map(roomMapper::toDomain)
                .sorted(RoomNumberOrder.NATURAL)
                .toList();
    }

    @Override
    public Optional<Room> findSellableById(UUID roomId) {
        return roomJpaRepository
                .findByIdAndStatusNot(roomId, RoomStatus.Inactive)
                .map(roomMapper::toDomain);
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

    @Override
    public long countByStatus(RoomStatus status) {
        return roomJpaRepository.countByStatus(status);
    }

    /** Columna pedida y, como desempate, el orden natural del número; se ordena en memoria por ese orden natural. */
    private static Comparator<Room> inventoryOrder(RoomInventoryFilter filter) {
        Comparator<Room> primary =
                switch (filter.sort()) {
                    case ROOM_NUMBER -> RoomNumberOrder.NATURAL;
                    case FLOOR -> Comparator.comparingInt(Room::getFloor);
                    case CATEGORY_ROOM -> Comparator.comparing(Room::getCategoryRoom);
                    case MAX_CAPACITY -> Comparator.comparingInt(Room::getMaxCapacity);
                    case BASE_RATE -> Comparator.comparing(Room::getBaseRate);
                    case STATUS -> Comparator.comparing(Room::getStatus);
                };
        return (filter.descending() ? primary.reversed() : primary).thenComparing(RoomNumberOrder.NATURAL);
    }

    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}

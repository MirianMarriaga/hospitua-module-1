package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia y consulta de habitaciones.
 */
public interface RoomRepositoryPort {

    Optional<Room> findById(UUID roomId);

    /** Las habitaciones que existen de esos ids, sin orden; los ids desconocidos se omiten. */
    List<Room> findAllById(Collection<UUID> roomIds);

    Room save(Room room);

    /**
     * Inserta una habitación nueva y la escribe de inmediato, para que un número repetido se detecte dentro de la
     * operación. Lanza {@code RoomNumberAlreadyExistsException} si otro registro ganó el número (registrar-habitacion
     * FR-004).
     */
    Room insert(Room room);

    boolean existsByRoomNumber(String roomNumber);

    /**
     * Inventario filtrado y ordenado (con el orden natural de {@code room_number} como desempate), en la página
     * {@code page} desde 1 (consultar-inventario T002).
     */
    PagedResultDto<Room> searchInventory(RoomInventoryFilter filter, int page, int size);

    /**
     * Habitaciones vendibles (todas menos las {@code Inactive}) de la categoría, en orden natural por número
     * (consultar-inventario FR-010). La exclusión va en la consulta, no en el invocador.
     */
    List<Room> findSellableByCategory(RoomCategory categoryRoom);

    /** La habitación si existe y no está {@code Inactive}; vacío en los dos casos contrarios (FR-011). */
    Optional<Room> findSellableById(UUID roomId);

    /**
     * Bloquea la fila de la habitación con SELECT ... FOR UPDATE (bloqueo pesimista).
     * Devuelve vacío si la habitación no existe.
     */
    Optional<Room> lockById(UUID roomId);

    /**
     * Consulta habitaciones por conjunto de estados, ordenadas en orden natural por room_number.
     */
    List<Room> findByStatusIn(Set<RoomStatus> statuses);

    /**
     * Consulta habitaciones excluyendo un estado, ordenadas en orden natural por room_number.
     */
    List<Room> findByStatusNot(RoomStatus status);
}

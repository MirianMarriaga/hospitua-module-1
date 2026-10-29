package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia y consulta de habitaciones.
 */
public interface RoomRepositoryPort {

    Optional<Room> findById(UUID roomId);

    Room save(Room room);

    /**
     * Inserta una habitación nueva y la escribe de inmediato, para que un número repetido se detecte dentro de la
     * operación. Lanza {@code RoomNumberAlreadyExistsException} si otro registro ganó el número (registrar-habitacion
     * FR-004).
     */
    Room insert(Room room);

    boolean existsByRoomNumber(String roomNumber);

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

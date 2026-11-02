package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.RoomDecommission;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de las bajas (plan-dar-de-baja-habitacion.md T003). La reactivación
 * (plan-marcar-habitacion-como-disponible.md) consulta la baja vigente y guarda la baja revertida.
 */
public interface RoomDecommissionRepositoryPort {

    /** Inserta una baja nueva o guarda la reversión de una existente. */
    RoomDecommission save(RoomDecommission decommission);

    Optional<RoomDecommission> findById(UUID decommissionId);

    /** La baja sin revertir de la habitación; toda habitación {@code Inactive} tiene una. */
    Optional<RoomDecommission> findCurrentByRoomId(UUID roomId);
}

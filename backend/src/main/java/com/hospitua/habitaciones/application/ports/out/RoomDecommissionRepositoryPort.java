package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.RoomDecommission;

/**
 * Persistencia de las bajas (plan-dar-de-baja-habitacion.md T003). La consulta de la baja vigente y su reactivación
 * las agrega plan-marcar-habitacion-como-disponible.md.
 */
public interface RoomDecommissionRepositoryPort {

    RoomDecommission save(RoomDecommission decommission);
}

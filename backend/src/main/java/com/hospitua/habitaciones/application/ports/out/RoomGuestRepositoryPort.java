package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.RoomGuest;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia inmutable de ocupantes físicos (huéspedes).
 * No expone operaciones de actualización ni eliminación (regla 4).
 */
public interface RoomGuestRepositoryPort {

    List<RoomGuest> saveAll(List<RoomGuest> guests);

    List<RoomGuest> findByStayId(UUID stayId);
}

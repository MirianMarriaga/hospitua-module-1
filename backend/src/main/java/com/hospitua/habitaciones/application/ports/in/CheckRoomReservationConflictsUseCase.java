package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reservas de Módulo 2 que impiden intervenir una habitación (consultar-reservas T027, T028). Puerto interno, sin
 * endpoint propio: lo usan el bloqueo técnico y la baja, fuera de su transacción. Ambos métodos lanzan
 * {@code ExternalModule2UnavailableException} si Módulo 2 no responde.
 */
public interface CheckRoomReservationConflictsUseCase {

    /** Reservas que se cruzan con el mantenimiento, del inicio al fin estimado (inclusivos). */
    ReservationConflictResult checkMaintenanceConflict(UUID roomId, LocalDate startDate, LocalDate endDate);

    /** Reservas desde hoy hasta hoy + 365 días, el rango máximo que acepta Módulo 2. */
    ReservationConflictResult checkDecommissionConflict(UUID roomId);
}

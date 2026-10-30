package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.CheckInValidationDto;
import java.util.UUID;

/**
 * Paso 1 del Check-In: valida la reserva y la habitación solo contra la copia local y el inventario, sin llamar a
 * Módulo 2 (plan-registrar-check-in.md T015; FR-003 a FR-005).
 */
public interface ValidateCheckInReservationUseCase {

    /**
     * La reserva, la habitación y el titular si se puede hacer el Check-In. Lanza {@code ReservationNotFoundException},
     * {@code InvalidCheckInDateException}, {@code StayAlreadyExistsException}, {@code RoomNotFoundException} o
     * {@code InvalidRoomTransitionException} según la primera regla que no se cumpla.
     */
    CheckInValidationDto validate(String reservationRef, UUID roomId);
}

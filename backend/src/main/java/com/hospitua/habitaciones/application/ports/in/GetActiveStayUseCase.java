package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.ActiveStayResponseDto;
import java.util.UUID;

/** Paso 1 del Check-Out: la estancia activa de una habitación de la reserva (plan-registrar-check-out.md T009). */
public interface GetActiveStayUseCase {

    /**
     * Lee solo la estancia, la habitación y sus huéspedes, sin llamar a Módulo 2 (FR-003).
     *
     * @throws com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException si el par no tiene una estancia
     *     activa
     * @throws com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException si la habitación no está
     *     {@code Occupied}
     */
    ActiveStayResponseDto getActiveStay(String reservationRef, UUID roomId);
}

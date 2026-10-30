package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.LocalDate;
import java.util.UUID;

/** Respuesta 201 del Check-In; no depende de Módulo 2, porque la notificación queda en el Outbox (FR-014). */
public record CheckInResultDto(
        UUID stayId,
        String reservationRef,
        UUID roomId,
        String roomNumber,
        RoomStatus roomStatus,
        LocalDate checkInDate) {}

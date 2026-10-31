package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.LocalDate;
import java.util.UUID;

/** Respuesta 200 del Check-Out; no depende de Módulo 2 ni de Módulo 3: los dos mensajes quedan en el Outbox (FR-014). */
public record CheckOutResultDto(
        UUID stayId, UUID roomId, String roomNumber, RoomStatus roomStatus, LocalDate checkOutDate) {}

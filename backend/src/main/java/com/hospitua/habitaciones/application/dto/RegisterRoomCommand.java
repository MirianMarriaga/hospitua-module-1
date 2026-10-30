package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomCategory;
import java.math.BigDecimal;

/**
 * Datos para registrar una habitación (registrar-habitacion FR-003). {@code administratorId} es el usuario actual
 * que queda como actor del periodo inicial (FR-010).
 */
public record RegisterRoomCommand(
        String roomNumber,
        Integer floor,
        RoomCategory categoryRoom,
        Integer maxCapacity,
        BigDecimal baseRate,
        String administratorId) {}

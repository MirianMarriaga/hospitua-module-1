package com.hospitua.habitaciones.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Edición de una habitación (plan-editar-habitacion.md): los valores del formulario tal como llegaron, la {@code version}
 * con la que se abrió y el Administrador actual. Un valor nulo deja el atributo como está; las reglas las aplica
 * {@code Room.applyChanges}.
 */
public record EditRoomCommand(
        UUID roomId,
        Integer version,
        String roomNumber,
        BigDecimal floor,
        String categoryRoom,
        BigDecimal maxCapacity,
        BigDecimal baseRate,
        String administratorId) {}

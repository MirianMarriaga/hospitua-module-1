package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.EditRoomCommand;
import com.hospitua.habitaciones.domain.exception.ConcurrentRoomUpdateException;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/rooms/{roomId}} (plan-editar-habitacion.md). Solo la {@code version} es obligatoria. Los
 * atributos no llevan reglas aquí: las aplica el dominio después de comprobar que la habitación está {@code Available},
 * para que una habitación ocupada responda su estado antes que un dato inválido (T015). El piso y la capacidad llegan
 * como decimal y la categoría como texto, para responder el mensaje de su regla también con un valor no entero o fuera
 * del catálogo.
 */
public record EditRoomRequest(
        @NotNull(message = ConcurrentRoomUpdateException.MESSAGE)
        Integer version,

        String roomNumber,
        BigDecimal floor,
        String categoryRoom,
        BigDecimal maxCapacity,
        BigDecimal baseRate) {

    public EditRoomCommand toCommand(UUID roomId, String administratorId) {
        return new EditRoomCommand(
                roomId, version, roomNumber, floor, categoryRoom, maxCapacity, baseRate, administratorId);
    }
}

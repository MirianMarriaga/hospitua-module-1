package com.hospitua.habitaciones.domain.model;

import java.util.Objects;

/**
 * Un atributo modificado al editar una habitación, con su valor anterior y el nuevo (plan-editar-habitacion.md, FR-007,
 * FR-008). El número y la categoría son texto; el piso y la capacidad, enteros; la tarifa, decimal.
 */
public record RoomAttributeChange(String field, Object previousValue, Object newValue) {

    public RoomAttributeChange {
        Objects.requireNonNull(field, "El campo no puede ser nulo");
        Objects.requireNonNull(previousValue, "El valor anterior no puede ser nulo");
        Objects.requireNonNull(newValue, "El valor nuevo no puede ser nulo");
    }
}

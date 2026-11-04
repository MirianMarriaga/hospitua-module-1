package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;

/**
 * Cuenta del personal sin contraseña (plan-gestionar-personal.md; FR-004, FR-006). {@code version} solo viaja donde el
 * contrato la pide (la cuenta individual y la respuesta de la edición); en el registro y en la lista es nula y se omite
 * ({@code non_null} del plan base).
 */
public record StaffAccountDto(String username, String fullName, StaffArea area, boolean active, Integer version) {

    /** Los campos de la confirmación del registro y de cada fila de la lista. */
    public static StaffAccountDto summary(StaffAccount account) {
        return new StaffAccountDto(
                account.getUsername(), account.getFullName(), account.getArea(), account.isActive(), null);
    }

    /** Con la {@code version} que la edición envía de vuelta (control optimista). */
    public static StaffAccountDto detail(StaffAccount account) {
        return new StaffAccountDto(
                account.getUsername(),
                account.getFullName(),
                account.getArea(),
                account.isActive(),
                account.getVersion());
    }
}

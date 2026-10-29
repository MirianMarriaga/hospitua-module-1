package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.StaffArea;

/**
 * Comando para registrar una nueva cuenta de personal (T022).
 * Sin anotaciones de validación de frameworks según la regla 1 de arquitectura hexagonal.
 */
public record RegisterStaffAccountCommand(String username, String rawPassword, String fullName, StaffArea area) {}

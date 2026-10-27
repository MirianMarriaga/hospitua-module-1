package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de petición para el inicio de sesión (POST /api/auth/login, T020).
 */
public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio.") String username,
        @NotBlank(message = "La contraseña es obligatoria.") String password) {}

package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

/**
 * DTO de respuesta para el inicio de sesión exitoso (T020).
 * Devuelve username, fullName y area; nunca la contraseña.
 */
public record LoginResponse(String username, String fullName, String area) {}

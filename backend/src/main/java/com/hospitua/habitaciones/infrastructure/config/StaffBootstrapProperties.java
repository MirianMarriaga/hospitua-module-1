package com.hospitua.habitaciones.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cuenta inicial de Administración {@code hospitua.bootstrap.admin.*} (T022), leída de variables de entorno. Si falta
 * un dato o no es válido, la aplicación no arranca.
 */
@Validated
@ConfigurationProperties("hospitua.bootstrap.admin")
public record StaffBootstrapProperties(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName) {}

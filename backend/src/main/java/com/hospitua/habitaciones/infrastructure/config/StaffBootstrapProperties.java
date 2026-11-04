package com.hospitua.habitaciones.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cuenta inicial de Administración {@code hospitua.bootstrap.admin.*} (T022), leída de variables de entorno. Si falta
 * un dato, la aplicación no arranca. La longitud de la contraseña no se valida aquí: Spring Boot escribe en el log el
 * valor rechazado; la valida {@code StaffAccountBootstrapper}, sin mostrarla (plan-gestionar-personal.md T015).
 */
@Validated
@ConfigurationProperties("hospitua.bootstrap.admin")
public record StaffBootstrapProperties(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String fullName) {}

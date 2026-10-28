package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades {@code hospitua.module2.*} hacia Módulo 2 (Reservas), con los timeouts de la regla 5. Sin URL la
 * aplicación no arranca.
 */
@Validated
@ConfigurationProperties("hospitua.module2")
public record Module2Properties(
        @NotBlank String baseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout) {}

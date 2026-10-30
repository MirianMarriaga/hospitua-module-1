package com.hospitua.habitaciones.infrastructure.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades {@code hospitua.daily-reservations.retry.*}: reintentos del listener de la cola diaria ante un error
 * temporal (plan-consultar-reservas.md T014; FR-003). Un valor inválido impide arrancar.
 */
@Validated
@ConfigurationProperties("hospitua.daily-reservations.retry")
public record DailyReservationRetryProperties(
        @DefaultValue("3") @PositiveOrZero long maxRetries,
        @DefaultValue("1s") @NotNull Duration initialInterval,
        @DefaultValue("2") @DecimalMin("1.0") double multiplier,
        @DefaultValue("10s") @NotNull Duration maxInterval) {}

package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades {@code hospitua.outbox.*} del worker (T012): reintentos antes de FAILED y tamaño del lote por ciclo.
 */
@Validated
@ConfigurationProperties("hospitua.outbox")
public record OutboxProperties(
        @DefaultValue("10") @Positive int maxRetries,
        @DefaultValue("100") @Positive int batchSize) {}

package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración para la integración REST con el Módulo 2 (Reservas).
 * Mapeadas desde hospitua.module2 con timeouts estrictos según la regla 5.
 */
@ConfigurationProperties(prefix = "hospitua.module2")
public record Module2Properties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public Module2Properties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(1);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(2);
        }
    }
}

package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración para la integración REST con el Módulo 3 (Liquidación/Facturación).
 * Mapeadas desde hospitua.module3 con timeouts estrictos según la regla 5.
 */
@ConfigurationProperties(prefix = "hospitua.module3")
public record Module3Properties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public Module3Properties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(1);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }
}

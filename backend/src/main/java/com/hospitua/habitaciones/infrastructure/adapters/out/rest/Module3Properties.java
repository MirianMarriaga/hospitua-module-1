package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades {@code hospitua.module3.*} hacia Módulo 3 (Facturación), con los timeouts de la regla 5. Sin URL la
 * aplicación no arranca. {@code internalKey} es la llave de integración que Módulo 3 le dio a Módulo 1 (cabecera
 * {@code X-Internal-Key}); vacía, no se envía.
 */
@Validated
@ConfigurationProperties("hospitua.module3")
public record Module3Properties(
        @NotBlank String baseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("") String internalKey) {}

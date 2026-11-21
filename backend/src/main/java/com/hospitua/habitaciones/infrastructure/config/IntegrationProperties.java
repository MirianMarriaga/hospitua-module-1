package com.hospitua.habitaciones.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Llaves de integración de los módulos que consultan a Módulo 1 ({@code hospitua.integration.*}; plan de integración,
 * Fase 1). Cada cliente tiene su llave, que envía en {@link #KEY_HEADER}, y las rutas que puede consultar, escritas
 * como {@code "MÉTODO /patrón"} igual que en los controllers. Un cliente sin llave deja sus rutas abiertas: así se
 * activa la exigencia por entorno, cuando el otro módulo ya envía la llave. Agregar un módulo es una entrada más.
 */
@Validated
@ConfigurationProperties("hospitua.integration")
public record IntegrationProperties(@DefaultValue Map<String, @Valid Client> clients) {

    /** Cabecera de la llave entre módulos, acordada con Módulo 3 el 19-11-2026; la usan también los clientes REST. */
    public static final String KEY_HEADER = "X-Internal-Key";

    /** Llave del cliente (vacía: rutas abiertas) y sus rutas, por ejemplo {@code GET /api/rooms/{roomId}/base-rate}. */
    public record Client(String key, @NotEmpty List<String> routes) {

        public boolean enforced() {
            return key != null && !key.isBlank();
        }
    }
}

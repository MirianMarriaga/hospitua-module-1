package com.hospitua.habitaciones.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del reloj del sistema fijado en la zona horaria de Colombia.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE_BOGOTA);
    }
}

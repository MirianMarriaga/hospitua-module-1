package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.domain.model.MaintenanceLimits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone {@link MaintenanceLimits} del dominio a partir de {@link MaintenanceProperties} (T019).
 */
@Configuration
public class MaintenanceConfig {

    @Bean
    MaintenanceLimits maintenanceLimits(MaintenanceProperties properties) {
        return properties.toLimits();
    }
}

package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.domain.model.MaintenanceLimits;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades de configuración para los límites de mantenimiento (T019).
 * Expone un bean MaintenanceLimits para desacoplar el dominio de la infraestructura.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "hospitua.maintenance")
public class MaintenanceProperties {

    private int maxRangeDays = 90;
    private int maxAdvanceDays = 365;

    @Bean
    public MaintenanceLimits maintenanceLimits() {
        return new MaintenanceLimits(maxRangeDays, maxAdvanceDays);
    }
}

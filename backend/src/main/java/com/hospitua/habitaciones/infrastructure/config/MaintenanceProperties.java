package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.domain.model.MaintenanceLimits;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades {@code hospitua.maintenance.*} (T019): duración máxima y antelación máxima de un mantenimiento, en días.
 */
@Validated
@ConfigurationProperties("hospitua.maintenance")
public record MaintenanceProperties(
        @DefaultValue("90") @Positive int maxRangeDays,
        @DefaultValue("365") @Positive int maxAdvanceDays) {

    public MaintenanceLimits toLimits() {
        return new MaintenanceLimits(maxRangeDays, maxAdvanceDays);
    }
}

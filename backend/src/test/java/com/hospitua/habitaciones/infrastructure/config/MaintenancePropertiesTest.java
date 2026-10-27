package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.model.MaintenanceLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaintenancePropertiesTest {

    @Test
    @DisplayName("MaintenanceProperties expone bean MaintenanceLimits con valores de configuración por defecto")
    void shouldExposeDefaultMaintenanceLimits() {
        MaintenanceProperties props = new MaintenanceProperties();
        assertThat(props.getMaxRangeDays()).isEqualTo(90);
        assertThat(props.getMaxAdvanceDays()).isEqualTo(365);

        MaintenanceLimits limits = props.maintenanceLimits();
        assertThat(limits).isNotNull();
        assertThat(limits.maxRangeDays()).isEqualTo(90);
        assertThat(limits.maxAdvanceDays()).isEqualTo(365);
    }

    @Test
    @DisplayName("MaintenanceProperties permite configurar valores personalizados")
    void shouldAllowCustomValues() {
        MaintenanceProperties props = new MaintenanceProperties();
        props.setMaxRangeDays(60);
        props.setMaxAdvanceDays(180);

        MaintenanceLimits limits = props.maintenanceLimits();
        assertThat(limits.maxRangeDays()).isEqualTo(60);
        assertThat(limits.maxAdvanceDays()).isEqualTo(180);
    }

    @Test
    @DisplayName("MaintenanceLimits rechaza valores menores o iguales a cero")
    void shouldRejectInvalidLimits() {
        assertThatThrownBy(() -> new MaintenanceLimits(0, 365))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRangeDays");

        assertThatThrownBy(() -> new MaintenanceLimits(90, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAdvanceDays");
    }
}

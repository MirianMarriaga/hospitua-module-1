package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.model.MaintenanceLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class MaintenancePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(MaintenanceConfig.class)
            .withBean(MaintenancePropertiesRegistrar.class);

    @EnableConfigurationProperties(MaintenanceProperties.class)
    static class MaintenancePropertiesRegistrar {}

    @Test
    @DisplayName("MaintenanceConfig expone MaintenanceLimits con los valores por defecto (90 / 365)")
    void shouldExposeDefaultMaintenanceLimits() {
        contextRunner.run(context -> {
            MaintenanceLimits limits = context.getBean(MaintenanceLimits.class);
            assertThat(limits.maxRangeDays()).isEqualTo(90);
            assertThat(limits.maxAdvanceDays()).isEqualTo(365);
        });
    }

    @Test
    @DisplayName("MaintenanceProperties toma valores personalizados de la configuración")
    void shouldAllowCustomValues() {
        contextRunner
                .withPropertyValues(
                        "hospitua.maintenance.max-range-days=60", "hospitua.maintenance.max-advance-days=180")
                .run(context -> {
                    MaintenanceLimits limits = context.getBean(MaintenanceLimits.class);
                    assertThat(limits.maxRangeDays()).isEqualTo(60);
                    assertThat(limits.maxAdvanceDays()).isEqualTo(180);
                });
    }

    @Test
    @DisplayName("Un límite no positivo impide arrancar")
    void shouldFailToStartWithNonPositiveLimit() {
        contextRunner
                .withPropertyValues("hospitua.maintenance.max-range-days=0")
                .run(context -> assertThat(context).hasFailed());
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

package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class StaffBootstrapPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Registrar.class)
            .withPropertyValues(
                    "hospitua.bootstrap.admin.username=admin",
                    "hospitua.bootstrap.admin.password=Admin2026!",
                    "hospitua.bootstrap.admin.full-name=Administrador");

    @EnableConfigurationProperties(StaffBootstrapProperties.class)
    static class Registrar {}

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "hospitua.bootstrap.admin.username=",
                "hospitua.bootstrap.admin.password=123",
                "hospitua.bootstrap.admin.full-name="
            })
    @DisplayName("Una propiedad de la cuenta inicial faltante o inválida impide arrancar (T022)")
    void invalidPropertyShouldPreventStartup(String invalidProperty) {
        contextRunner
                .withPropertyValues(invalidProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Con propiedades válidas el contexto arranca")
    void validPropertiesShouldStart() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }
}

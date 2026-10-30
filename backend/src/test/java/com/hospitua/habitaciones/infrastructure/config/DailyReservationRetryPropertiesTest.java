package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class DailyReservationRetryPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withBean(RetryPropertiesRegistrar.class);

    @EnableConfigurationProperties(DailyReservationRetryProperties.class)
    static class RetryPropertiesRegistrar {}

    @Test
    @DisplayName("Por defecto: 3 reintentos desde 1 s, duplicando la espera hasta 10 s (FR-003)")
    void shouldExposeDefaults() {
        contextRunner.run(context -> assertThat(context.getBean(DailyReservationRetryProperties.class))
                .isEqualTo(new DailyReservationRetryProperties(3, Duration.ofSeconds(1), 2, Duration.ofSeconds(10))));
    }

    @Test
    @DisplayName("Un número de reintentos negativo o un multiplicador menor que 1 impiden arrancar")
    void shouldFailToStartWithInvalidValues() {
        contextRunner
                .withPropertyValues("hospitua.daily-reservations.retry.max-retries=-1")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues("hospitua.daily-reservations.retry.multiplier=0.5")
                .run(context -> assertThat(context).hasFailed());
    }
}

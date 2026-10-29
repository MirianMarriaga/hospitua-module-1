package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxBackoffPolicyTest {

    private final OutboxBackoffPolicy policy = new OutboxBackoffPolicy();

    @Test
    @DisplayName("Espera creciente de 1s, 2s, 4s... hasta el tope de 5 minutos (300 segundos)")
    void delayShouldGrowExponentiallyWithCapAt300Seconds() {
        assertThat(policy.calculateDelay(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.calculateDelay(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.calculateDelay(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.calculateDelay(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.calculateDelay(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(policy.calculateDelay(6)).isEqualTo(Duration.ofSeconds(32));
        assertThat(policy.calculateDelay(7)).isEqualTo(Duration.ofSeconds(64));
        assertThat(policy.calculateDelay(8)).isEqualTo(Duration.ofSeconds(128));
        assertThat(policy.calculateDelay(9)).isEqualTo(Duration.ofSeconds(256));
        // A partir de 10 superaría 512s, por lo que aplica el tope de 300s (5 min)
        assertThat(policy.calculateDelay(10)).isEqualTo(Duration.ofSeconds(300));
        assertThat(policy.calculateDelay(15)).isEqualTo(Duration.ofSeconds(300));
    }
}

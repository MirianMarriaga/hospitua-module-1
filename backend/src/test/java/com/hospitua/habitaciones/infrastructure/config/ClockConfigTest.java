package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    private final ClockConfig clockConfig = new ClockConfig();

    @Test
    @DisplayName("El bean Clock debe tener la zona America/Bogota y offset -05:00")
    void clockShouldUseBogotaZoneAndNegativeFiveOffset() {
        Clock clock = clockConfig.clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Bogota"));

        ZoneOffset offset = clock.getZone().getRules().getOffset(clock.instant());
        assertThat(offset).isEqualTo(ZoneOffset.of("-05:00"));
    }
}

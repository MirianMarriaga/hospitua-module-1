package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ApplicationConfigurationProfilesTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    @DisplayName("application.yml no debe contener hospitua.maintenance.fallback-apply-time")
    void shouldNotContainFallbackApplyTime() throws IOException {
        List<PropertySource<?>> propertySources =
                loader.load("application.yml", new ClassPathResource("application.yml"));

        for (PropertySource<?> source : propertySources) {
            assertThat(source.getProperty("hospitua.maintenance.fallback-apply-time"))
                    .as("fallback-apply-time debe ser eliminado según regla 10")
                    .isNull();
        }
    }

    @Test
    @DisplayName("Perfil dev debe incluir classpath:db/dev-data en las ubicaciones de Flyway")
    void devProfileShouldIncludeDevDataLocation() throws IOException {
        List<PropertySource<?>> propertySources =
                loader.load("application.yml", new ClassPathResource("application.yml"));

        PropertySource<?> devSource = propertySources.stream()
                .filter(source -> "dev".equals(source.getProperty("spring.config.activate.on-profile")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el perfil dev"));

        Object locations = devSource.getProperty("spring.flyway.locations");
        assertThat(locations).isNotNull();
        assertThat(locations.toString()).contains("classpath:db/dev-data");
        assertThat(locations.toString()).contains("classpath:db/migration");
    }

    @Test
    @DisplayName("Perfiles dev, test y prod deben configurar URLs de Módulo 2 y Módulo 3")
    void profilesShouldConfigureModule2And3Urls() throws IOException {
        List<PropertySource<?>> propertySources =
                loader.load("application.yml", new ClassPathResource("application.yml"));

        List<String> expectedProfiles = List.of("dev", "test", "prod");
        for (String profile : expectedProfiles) {
            PropertySource<?> source = propertySources.stream()
                    .filter(s -> profile.equals(s.getProperty("spring.config.activate.on-profile")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se encontró el perfil: " + profile));

            assertThat(source.getProperty("hospitua.module2.base-url"))
                    .as("module2 base-url en " + profile)
                    .isNotNull();
            assertThat(source.getProperty("hospitua.module3.base-url"))
                    .as("module3 base-url en " + profile)
                    .isNotNull();
        }
    }
}

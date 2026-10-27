package com.hospitua.habitaciones.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades de configuración para la cuenta inicial de Administrador (T022).
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "hospitua.bootstrap.admin")
public class StaffBootstrapProperties {

    private String username;
    private String password;
    private String fullName;
}

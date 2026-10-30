package com.hospitua.habitaciones;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Margen para equipos con poca CPU libre, donde RabbitMQ tarda más del minuto por defecto en arrancar. */
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).withStartupTimeout(STARTUP_TIMEOUT);
    }

    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                .withStartupTimeout(STARTUP_TIMEOUT);
    }
}

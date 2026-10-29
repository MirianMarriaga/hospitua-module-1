package com.hospitua.habitaciones.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la ejecución de tareas programadas (@Scheduled).
 * Se puede deshabilitar en pruebas configurando hospitua.outbox.worker.enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "hospitua.outbox.worker.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}

package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.ScheduleTechnicalBlockCommand;
import com.hospitua.habitaciones.application.dto.TechnicalBlockCreatedDto;

/** Programar Bloqueo Técnico para Habitación (plan-programar-bloqueo-tecnico-para-habitacion.md T020). */
public interface ScheduleTechnicalBlockUseCase {

    /**
     * Registra el informe en {@code Scheduled} o, si inicia hoy, lo aplica: la habitación pasa a {@code TechnicalBlock}.
     * Publica el aviso {@code SCHEDULED} a Módulo 2.
     */
    TechnicalBlockCreatedDto schedule(ScheduleTechnicalBlockCommand command);
}

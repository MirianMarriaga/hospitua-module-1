package com.hospitua.habitaciones.application.event;

import java.time.LocalDate;

/**
 * Fin de la ingesta de la lista de las 00:00, con su día operativo. Se publica dentro de la transacción de la ingesta;
 * lo consume mantenimiento después del commit (regla 10 del plan base).
 */
public record DailyReservationListIngestedEvent(LocalDate operationalDate) {}

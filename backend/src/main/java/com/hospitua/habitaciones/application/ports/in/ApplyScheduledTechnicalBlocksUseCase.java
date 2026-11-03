package com.hospitua.habitaciones.application.ports.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Aplicar los bloqueos técnicos programados (plan-programar-bloqueo-tecnico-para-habitacion.md T021): el trabajo diario
 * y la aplicación al terminar una limpieza. Nunca sobrescribe un estado distinto de {@code Available} (FR-016).
 */
public interface ApplyScheduledTechnicalBlocksUseCase {

    /**
     * Caduca los informes {@code Scheduled} que terminaron antes de {@code today} y aplica cada uno que cubre hoy, cada
     * habitación en su propia transacción. Sin transacción propia; repetirlo el mismo día no cambia nada.
     */
    void runDaily(LocalDate today);

    /**
     * Bloquea la fila y, si la habitación está {@code Available} y tiene un informe {@code Scheduled} que cubre
     * {@code today}, la pasa a {@code TechnicalBlock} con actor nulo y marca el informe {@code Applied}. Propagación
     * {@code REQUIRED}: desde el fin de limpieza se une a su transacción.
     *
     * @return {@code true} si aplicó un bloqueo
     */
    boolean applyForRoom(UUID roomId, LocalDate today);
}

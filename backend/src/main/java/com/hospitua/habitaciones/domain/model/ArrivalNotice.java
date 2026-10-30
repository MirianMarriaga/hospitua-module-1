package com.hospitua.habitaciones.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Aviso de una fila de Llegadas según el estado de la habitación (plan-consultar-panel-recepcion.md, FR-005). Se
 * calcula solo con el estado: no publica nada hacia Módulo 2 (Marcar habitación como reservada, esc. 7 y 8).
 */
public record ArrivalNotice(ArrivalNoticeType type, String text) {

    public ArrivalNotice {
        Objects.requireNonNull(type, "El tipo del aviso no puede ser nulo");
        Objects.requireNonNull(text, "El texto del aviso no puede ser nulo");
    }

    /**
     * "No disponible: [Estado]" en mantenimiento o fuera de servicio, "[Estado]: se apartará al terminar la limpieza"
     * si está ocupada o en limpieza, y sin aviso en {@code Reserved} o {@code Available}.
     */
    public static Optional<ArrivalNotice> forStatus(RoomStatus status) {
        return switch (status) {
            case DisabledForRepairs, TechnicalBlock, Inactive ->
                Optional.of(new ArrivalNotice(ArrivalNoticeType.UNAVAILABLE, "No disponible: " + status.displayName()));
            case Occupied, PendingCleaning, InCleaning ->
                Optional.of(new ArrivalNotice(
                        ArrivalNoticeType.PENDING_ASSIGNMENT,
                        status.displayName() + ": se apartará al terminar la limpieza"));
            case Reserved, Available -> Optional.empty();
        };
    }
}

package com.hospitua.habitaciones.domain.model;

/** Tipo del aviso de una fila de Llegadas (plan-consultar-panel-recepcion.md, FR-005). */
public enum ArrivalNoticeType {
    /** Alerta: la habitación está en mantenimiento o fuera de servicio y no se pudo apartar. */
    UNAVAILABLE,
    /** Informativo: la habitación está ocupada o en limpieza y se apartará al terminar la limpieza. */
    PENDING_ASSIGNMENT
}

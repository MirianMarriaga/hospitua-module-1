package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.RoomAuditEntry;

/**
 * Puerto de salida para registrar entradas en la bitácora de auditoría de habitaciones.
 */
public interface RoomAuditLogRepositoryPort {

    void record(RoomAuditEntry entry);
}

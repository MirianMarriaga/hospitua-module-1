package com.hospitua.habitaciones.domain.exception;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.Map;

/**
 * La habitación no tiene un daño reportado vigente: no está en {@code DisabledForRepairs} (404
 * DAMAGE_REPORT_NOT_FOUND, con details.currentStatus; inhabilitada-por-reparaciones FR-006).
 */
public final class DamageReportNotFoundException extends DomainException {

    public DamageReportNotFoundException(RoomStatus currentStatus) {
        super(
                DomainError.DAMAGE_REPORT_NOT_FOUND,
                "Esta habitación no tiene un daño reportado.",
                Map.of("currentStatus", currentStatus.name()));
    }
}

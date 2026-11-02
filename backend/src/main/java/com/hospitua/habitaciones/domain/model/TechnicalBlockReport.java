package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Bloqueo técnico programado para una habitación (plan-programar-bloqueo-tecnico-para-habitacion.md T003). Sus datos no
 * se editan y la programación no se cancela (FR-017): solo cambia de estado, {@code Scheduled} → {@code Applied} →
 * {@code Completed} o {@code Scheduled} → {@code Expired}. El rango {@code [inicio, fin estimado]} es inclusivo.
 */
public final class TechnicalBlockReport {

    private final UUID id;
    private final UUID roomId;
    private final String maintenanceStaffMemberId;
    private final String reason;
    private final LocalDate startDate;
    private final LocalDate estimatedEndDate;
    private final OffsetDateTime reportDateTime;
    private TechnicalBlockStatus status;

    public TechnicalBlockReport(
            UUID id,
            UUID roomId,
            String maintenanceStaffMemberId,
            String reason,
            LocalDate startDate,
            LocalDate estimatedEndDate,
            OffsetDateTime reportDateTime,
            TechnicalBlockStatus status) {
        this.id = Objects.requireNonNull(id, "El id del informe no puede ser nulo");
        this.roomId = Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        this.maintenanceStaffMemberId =
                Objects.requireNonNull(maintenanceStaffMemberId, "El miembro no puede ser nulo");
        this.reason = Objects.requireNonNull(reason, "El motivo no puede ser nulo");
        this.startDate = Objects.requireNonNull(startDate, "La fecha de inicio no puede ser nula");
        this.estimatedEndDate = Objects.requireNonNull(estimatedEndDate, "La fecha estimada de fin no puede ser nula");
        if (estimatedEndDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha estimada de fin no puede ser anterior a la de inicio");
        }
        this.reportDateTime = Objects.requireNonNull(reportDateTime, "La fecha del informe no puede ser nula");
        this.status = Objects.requireNonNull(status, "El estado no puede ser nulo");
    }

    /** Informe nuevo en {@code Scheduled}; las fechas y el motivo ya los validó la programación. */
    public static TechnicalBlockReport schedule(
            UUID roomId,
            String maintenanceStaffMemberId,
            String reason,
            LocalDate startDate,
            LocalDate estimatedEndDate,
            OffsetDateTime reportDateTime) {
        return new TechnicalBlockReport(
                UUID.randomUUID(),
                roomId,
                maintenanceStaffMemberId,
                reason,
                startDate,
                estimatedEndDate,
                reportDateTime,
                TechnicalBlockStatus.Scheduled);
    }

    /** El rango del mantenimiento contiene el día (programación con inicio hoy, trabajo diario y fin de limpieza). */
    public boolean covers(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(estimatedEndDate);
    }

    /** En curso después de su fecha estimada de fin: el panel de mantenimiento lo marca como vencido. */
    public boolean isOverdue(LocalDate today) {
        return status == TechnicalBlockStatus.Applied && estimatedEndDate.isBefore(today);
    }

    /** La habitación pasó a {@code TechnicalBlock}. */
    public void apply() {
        moveTo(TechnicalBlockStatus.Applied);
    }

    /** La reparación terminó (plan-confirmar-reparacion-finalizada.md). */
    public void complete() {
        moveTo(TechnicalBlockStatus.Completed);
    }

    /** Pasó la fecha estimada de fin sin poder aplicarse. */
    public void expire() {
        moveTo(TechnicalBlockStatus.Expired);
    }

    private void moveTo(TechnicalBlockStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("El informe " + id + " no puede pasar de " + status + " a " + target);
        }
        status = target;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getMaintenanceStaffMemberId() {
        return maintenanceStaffMemberId;
    }

    public String getReason() {
        return reason;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEstimatedEndDate() {
        return estimatedEndDate;
    }

    public OffsetDateTime getReportDateTime() {
        return reportDateTime;
    }

    public TechnicalBlockStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TechnicalBlockReport that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

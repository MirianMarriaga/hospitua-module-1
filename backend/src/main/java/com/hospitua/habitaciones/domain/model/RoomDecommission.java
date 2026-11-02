package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.InvalidDecommissionReasonException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Baja de una habitación (plan-dar-de-baja-habitacion.md T003): motivo, Administrador y fecha (FR-007, FR-008).
 * {@code decommissionedBy} es nulo solo en las bajas que carga la semilla; la reactivación
 * (plan-marcar-habitacion-como-disponible.md) completa {@code reactivatedBy} y {@code reactivationDateTime}.
 */
public final class RoomDecommission {

    public static final int MAX_DETAIL_LENGTH = 500;

    private final UUID id;
    private final UUID roomId;
    private final DecommissionReason reasonCategory;
    private final String reasonDetail;
    private final String decommissionedBy;
    private final OffsetDateTime decommissionDateTime;
    private final String reactivatedBy;
    private final OffsetDateTime reactivationDateTime;

    public RoomDecommission(
            UUID id,
            UUID roomId,
            DecommissionReason reasonCategory,
            String reasonDetail,
            String decommissionedBy,
            OffsetDateTime decommissionDateTime,
            String reactivatedBy,
            OffsetDateTime reactivationDateTime) {
        this.id = Objects.requireNonNull(id, "El id de la baja no puede ser nulo");
        this.roomId = Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        this.reasonCategory = Objects.requireNonNull(reasonCategory, "La categoría del motivo no puede ser nula");
        this.reasonDetail = reasonDetail;
        this.decommissionedBy = decommissionedBy;
        this.decommissionDateTime =
                Objects.requireNonNull(decommissionDateTime, "La fecha de la baja no puede ser nula");
        this.reactivatedBy = reactivatedBy;
        this.reactivationDateTime = reactivationDateTime;
    }

    /** Baja nueva con la marca del periodo {@code Inactive} que abrió la transición (FR-007, FR-011). */
    public static RoomDecommission decommission(
            UUID roomId,
            DecommissionReason reasonCategory,
            String reasonDetail,
            String administratorId,
            OffsetDateTime decommissionDateTime) {
        return new RoomDecommission(
                UUID.randomUUID(),
                roomId,
                reasonCategory,
                normalizeDetail(reasonCategory, reasonDetail),
                Objects.requireNonNull(administratorId, "El Administrador no puede ser nulo"),
                decommissionDateTime,
                null,
                null);
    }

    /** Una baja revertida ya no es la vigente de su habitación (plan-marcar-habitacion-como-disponible.md, FR-010). */
    public boolean isReactivated() {
        return reactivationDateTime != null;
    }

    /** La misma baja, revertida por el Administrador con la marca del periodo {@code Available} del historial. */
    public RoomDecommission reactivate(String administratorId, OffsetDateTime reactivationDateTime) {
        if (isReactivated()) {
            throw new IllegalStateException("La baja " + id + " ya fue revertida.");
        }
        return new RoomDecommission(
                id,
                roomId,
                reasonCategory,
                reasonDetail,
                decommissionedBy,
                decommissionDateTime,
                Objects.requireNonNull(administratorId, "El Administrador no puede ser nulo"),
                Objects.requireNonNull(reactivationDateTime, "La fecha de la reactivación no puede ser nula"));
    }

    /**
     * Detalle sin espacios al inicio ni al final; vacío o solo espacios queda nulo. Es obligatorio con {@code OTHER} y
     * admite máximo 500 caracteres (FR-008).
     *
     * @throws InvalidDecommissionReasonException si falta con {@code OTHER} o es demasiado largo
     */
    public static String normalizeDetail(DecommissionReason reasonCategory, String reasonDetail) {
        String normalized = reasonDetail == null || reasonDetail.isBlank() ? null : reasonDetail.strip();
        if (normalized == null && reasonCategory.requiresDetail()) {
            throw new InvalidDecommissionReasonException(InvalidDecommissionReasonException.DETAIL_REQUIRED);
        }
        if (normalized != null && normalized.length() > MAX_DETAIL_LENGTH) {
            throw new InvalidDecommissionReasonException(InvalidDecommissionReasonException.DETAIL_TOO_LONG);
        }
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public DecommissionReason getReasonCategory() {
        return reasonCategory;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public String getDecommissionedBy() {
        return decommissionedBy;
    }

    public OffsetDateTime getDecommissionDateTime() {
        return decommissionDateTime;
    }

    public String getReactivatedBy() {
        return reactivatedBy;
    }

    public OffsetDateTime getReactivationDateTime() {
        return reactivationDateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomDecommission that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

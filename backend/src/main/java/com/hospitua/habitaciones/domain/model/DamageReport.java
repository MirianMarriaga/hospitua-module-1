package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.InvalidDamageDescriptionException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Reporte del daño que inhabilita una habitación por reparaciones (plan-marcar-habitacion-inhabilitada-por-reparaciones.md
 * T002). Es inmutable: no se modifica ni se elimina (FR-010). {@code authorId} es el {@code username} del autor y
 * {@code reportDateTime} la marca del periodo {@code DisabledForRepairs} en el historial (FR-008, FR-009).
 */
public final class DamageReport {

    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private final UUID id;
    private final UUID roomId;
    private final String authorId;
    private final String damageDescription;
    private final OffsetDateTime reportDateTime;

    public DamageReport(
            UUID id, UUID roomId, String authorId, String damageDescription, OffsetDateTime reportDateTime) {
        this.id = Objects.requireNonNull(id, "El id del reporte no puede ser nulo");
        this.roomId = Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        this.authorId = Objects.requireNonNull(authorId, "El autor no puede ser nulo");
        this.damageDescription = normalizeDescription(damageDescription);
        this.reportDateTime = Objects.requireNonNull(reportDateTime, "La fecha del reporte no puede ser nula");
    }

    /** Reporte nuevo con la marca que puso el servidor al inhabilitar la habitación. */
    public static DamageReport report(
            UUID roomId, String authorId, String damageDescription, OffsetDateTime reportDateTime) {
        return new DamageReport(UUID.randomUUID(), roomId, authorId, damageDescription, reportDateTime);
    }

    /**
     * Descripción sin espacios al inicio ni al final; obligatoria y de máximo 500 caracteres (FR-003, caso límite 2).
     *
     * @throws InvalidDamageDescriptionException si es nula, vacía, solo espacios o demasiado larga
     */
    public static String normalizeDescription(String damageDescription) {
        String normalized = damageDescription == null ? "" : damageDescription.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidDamageDescriptionException();
        }
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getDamageDescription() {
        return damageDescription;
    }

    public OffsetDateTime getReportDateTime() {
        return reportDateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DamageReport that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

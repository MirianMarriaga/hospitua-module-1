package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException;
import com.hospitua.habitaciones.domain.exception.TaskNotOwnedException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Tarea de limpieza de una habitación por un miembro del personal (plan-marcar-habitacion-en-limpieza.md T002; regla 9
 * del plan base). Está abierta mientras no tiene {@code endDateTime}; al cerrarse recibe a la vez la marca de fin y su
 * {@link CleaningTaskOutcome}. {@code cleaningStaffMemberId} es el {@code username} del miembro.
 */
public class CleaningTask {

    private final UUID id;
    private final UUID roomId;
    private final String cleaningStaffMemberId;
    private final OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;
    private CleaningTaskOutcome outcome;

    public CleaningTask(
            UUID id,
            UUID roomId,
            String cleaningStaffMemberId,
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            CleaningTaskOutcome outcome) {
        this.id = Objects.requireNonNull(id, "El id de la tarea no puede ser nulo");
        this.roomId = Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        this.cleaningStaffMemberId =
                Objects.requireNonNull(cleaningStaffMemberId, "El miembro del personal no puede ser nulo");
        this.startDateTime = Objects.requireNonNull(startDateTime, "La fecha de inicio no puede ser nula");
        if ((endDateTime == null) != (outcome == null)) {
            throw new IllegalArgumentException(
                    "Una tarea cerrada tiene fecha de fin y resultado; una abierta, ninguno");
        }
        this.endDateTime = endDateTime;
        this.outcome = outcome;
    }

    /** Tarea nueva y abierta del miembro sobre la habitación. */
    public static CleaningTask start(UUID roomId, String memberId, OffsetDateTime startDateTime) {
        return new CleaningTask(UUID.randomUUID(), roomId, memberId, startDateTime, null, null);
    }

    public boolean isOpen() {
        return endDateTime == null;
    }

    /**
     * Solo el dueño cierra su tarea, y solo si sigue abierta. Lanza {@link TaskNotOwnedException} antes que
     * {@link TaskAlreadyClosedException}: a quien no es el dueño no se le dice cómo está la tarea.
     */
    public void ensureClosableBy(String memberId) {
        if (!cleaningStaffMemberId.equals(memberId)) {
            throw new TaskNotOwnedException("Esta habitación la está limpiando otro compañero.");
        }
        if (!isOpen()) {
            throw new TaskAlreadyClosedException();
        }
    }

    /**
     * Cierra la tarea con su resultado; liberarla es {@code close(Released, marca)}. Una marca de fin anterior al
     * inicio es un error de programación ({@link IllegalStateException}, regla 7 del plan base).
     */
    public void close(CleaningTaskOutcome outcome, OffsetDateTime endDateTime) {
        Objects.requireNonNull(outcome, "El resultado no puede ser nulo");
        Objects.requireNonNull(endDateTime, "La fecha de fin no puede ser nula");
        if (!isOpen()) {
            throw new IllegalStateException("La tarea " + id + " ya está cerrada");
        }
        if (endDateTime.isBefore(startDateTime)) {
            throw new IllegalStateException("La tarea " + id + " no puede terminar antes de empezar");
        }
        this.endDateTime = endDateTime;
        this.outcome = outcome;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getCleaningStaffMemberId() {
        return cleaningStaffMemberId;
    }

    public OffsetDateTime getStartDateTime() {
        return startDateTime;
    }

    public OffsetDateTime getEndDateTime() {
        return endDateTime;
    }

    public CleaningTaskOutcome getOutcome() {
        return outcome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CleaningTask that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

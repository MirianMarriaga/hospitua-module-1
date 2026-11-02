package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistencia de los informes de bloqueo técnico (plan-programar-bloqueo-tecnico-para-habitacion.md T003). Sus
 * escrituras ocurren con la fila de la habitación bloqueada (regla 13 del plan base), salvo la caducidad, que es un solo
 * {@code UPDATE} sobre informes que nadie más modifica.
 */
public interface TechnicalBlockReportRepositoryPort {

    /** Inserta un informe nuevo o guarda su cambio de estado. */
    TechnicalBlockReport save(TechnicalBlockReport report);

    /**
     * Informes {@code Scheduled} o {@code Applied} de la habitación que se cruzan con {@code [from, to]} (días
     * inclusivos), ordenados por inicio (plan-consultar-informacion-mantenimientos.md).
     */
    List<TechnicalBlockReport> findActiveOverlapping(UUID roomId, LocalDate from, LocalDate to);

    /** El informe {@code Applied} de cada habitación; las habitaciones sin uno no aparecen. */
    Map<UUID, TechnicalBlockReport> findAppliedByRoomIds(Collection<UUID> roomIds);

    /** El informe {@code Scheduled} de inicio más temprano de cada habitación; las que no tienen no aparecen. */
    Map<UUID, TechnicalBlockReport> findEarliestScheduledByRoomIds(Collection<UUID> roomIds);

    /** Habitaciones con un informe {@code Scheduled} cuyo rango contiene {@code today} (trabajo diario). */
    List<UUID> findRoomIdsWithScheduledCovering(LocalDate today);

    /** Marca {@code Expired} los {@code Scheduled} cuyo fin estimado ya pasó; devuelve cuántos caducaron. */
    int expireScheduledEndedBefore(LocalDate today);
}

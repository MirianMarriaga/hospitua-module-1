package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
import com.hospitua.habitaciones.domain.model.PendingArrival;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Puerto de salida de la copia local de las reservas del día y de su bitácora de mensajes
 * (plan-consultar-reservas.md T007, T011). Recepción y check-in solo leen de aquí, sin llamar a Módulo 2.
 */
public interface DailyReservationRepositoryPort {

    /** Guarda la reserva con sus habitaciones; si ya existe, la reemplaza completa (ADDED y UPDATED). */
    void save(DailyReservation reservation);

    Optional<DailyReservation> findByReservationRef(String reservationRef);

    /** Todas las reservas de la copia local con sus habitaciones. */
    List<DailyReservation> findAll();

    /** Quita la reserva y sus habitaciones (REMOVED); no hace nada si no existe. */
    void deleteByReservationRef(String reservationRef);

    /** Vacía la copia local antes de guardar la lista de las 00:00; la bitácora de mensajes se conserva. */
    void purge();

    boolean isMessageLogged(String messageId);

    void logMessage(DailyReservationMessageLog entry);

    /** Día de la copia local: el {@code operationalDate} de la última lista del día registrada. */
    Optional<LocalDate> findLatestDailyListDate();

    /** Mayor {@code sequenceNumber} registrado en el día operativo. */
    OptionalLong findLastSequence(LocalDate operationalDate);

    /** Mayor {@code sequenceNumber} registrado para la reserva en el día, aunque un REMOVED la haya borrado. */
    OptionalLong findLastSequenceForReservation(LocalDate operationalDate, String reservationRef);

    /**
     * Reserva del día ({@code start_date = operationalDate}) asignada a la habitación que todavía no tiene una
     * {@code Stay} para esa habitación (llegada pendiente); la usa Confirmar fin de limpieza.
     */
    Optional<String> findPendingArrivalReservationRef(UUID roomId, LocalDate operationalDate);

    /** Si se registró la lista de las 00:00 ({@code reserva.lista-del-dia}) de ese día operativo (FR-011). */
    boolean isDailyListReceived(LocalDate operationalDate);

    /**
     * Reservas con {@code start_date = operationalDate} y al menos una habitación sin {@code Stay}, en orden de
     * {@code reservationRef} y con sus habitaciones por número. {@code normalizedQuery} va sin espacios sobrantes y en
     * minúsculas; nulo devuelve todas, y si no, filtra por referencia o documento exactos o por parte del nombre, del
     * apellido o del nombre completo del titular.
     */
    List<PendingArrival> searchPendingArrivals(LocalDate operationalDate, String normalizedQuery);

    /**
     * Habitaciones de las reservas del día sin {@code Stay} que existen en el inventario: las filas de la pestaña
     * Llegadas sin filtro (KPI "Llegadas de hoy").
     */
    long countPendingArrivalRooms(LocalDate operationalDate);
}

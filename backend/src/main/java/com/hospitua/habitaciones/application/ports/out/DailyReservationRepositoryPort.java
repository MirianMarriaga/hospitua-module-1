package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
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
}

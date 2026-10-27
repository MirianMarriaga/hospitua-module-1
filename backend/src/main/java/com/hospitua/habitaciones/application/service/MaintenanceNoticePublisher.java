package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MaintenanceNoticePayload;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.domain.model.Room;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicador de avisos de mantenimiento hacia Módulo 2 a través del patrón Outbox (T024).
 * Encola los mensajes en outbox_notification con routing key habitacion.mantenimiento
 * y eventType MAINTENANCE_NOTICE dentro de la transacción obligatoria del invocador (MANDATORY).
 */
@Service
public class MaintenanceNoticePublisher {

    public static final String ROUTING_KEY = "habitacion.mantenimiento";
    public static final String EVENT_TYPE = "MAINTENANCE_NOTICE";

    private final OutboxEventPublisherPort outboxEventPublisherPort;

    public MaintenanceNoticePublisher(OutboxEventPublisherPort outboxEventPublisherPort) {
        this.outboxEventPublisherPort = Objects.requireNonNull(
                outboxEventPublisherPort, "El puerto OutboxEventPublisherPort no puede ser nulo");
    }

    /**
     * Publica un aviso de mantenimiento programado o inhabilitación por daño.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishScheduled(UUID maintenanceId, Room room, LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(maintenanceId, "El maintenanceId no puede ser nulo");
        Objects.requireNonNull(room, "La habitación no puede ser nula");
        Objects.requireNonNull(startDate, "La fecha de inicio no puede ser nula");
        Objects.requireNonNull(endDate, "La fecha de fin no puede ser nula");

        MaintenanceNoticePayload payload = MaintenanceNoticePayload.scheduled(
                maintenanceId, room.getId(), room.getCategoryRoom().name(), startDate, endDate);

        outboxEventPublisherPort.enqueue(ROUTING_KEY, EVENT_TYPE, payload);
    }

    /**
     * Publica un aviso de mantenimiento actualizado (ej. fin de bloqueo en fecha distinta de la estimada).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishUpdated(
            UUID maintenanceId,
            Room room,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate previousStartDate,
            LocalDate previousEndDate) {
        Objects.requireNonNull(maintenanceId, "El maintenanceId no puede ser nulo");
        Objects.requireNonNull(room, "La habitación no puede ser nula");
        Objects.requireNonNull(startDate, "La fecha de inicio no puede ser nula");
        Objects.requireNonNull(endDate, "La fecha de fin no puede ser nula");
        Objects.requireNonNull(previousStartDate, "La fecha previa de inicio no puede ser nula");
        Objects.requireNonNull(previousEndDate, "La fecha previa de fin no puede ser nula");

        MaintenanceNoticePayload payload = MaintenanceNoticePayload.updated(
                maintenanceId,
                room.getId(),
                room.getCategoryRoom().name(),
                startDate,
                endDate,
                previousStartDate,
                previousEndDate);

        outboxEventPublisherPort.enqueue(ROUTING_KEY, EVENT_TYPE, payload);
    }

    /**
     * Publica un aviso de mantenimiento cancelado (ej. fin de inhabilitación por reparaciones de un daño).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCancelled(UUID maintenanceId, Room room, LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(maintenanceId, "El maintenanceId no puede ser nulo");
        Objects.requireNonNull(room, "La habitación no puede ser nula");
        Objects.requireNonNull(startDate, "La fecha de inicio no puede ser nula");
        Objects.requireNonNull(endDate, "La fecha de fin no puede ser nula");

        MaintenanceNoticePayload payload = MaintenanceNoticePayload.cancelled(
                maintenanceId, room.getId(), room.getCategoryRoom().name(), startDate, endDate);

        outboxEventPublisherPort.enqueue(ROUTING_KEY, EVENT_TYPE, payload);
    }
}

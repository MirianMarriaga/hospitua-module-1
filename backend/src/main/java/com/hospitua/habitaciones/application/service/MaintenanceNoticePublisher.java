package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MaintenanceNoticePayload;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.domain.model.Room;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encola el aviso de mantenimiento hacia Módulo 2 en la misma transacción que el hecho que lo origina (T024): si esa
 * transacción se revierte, el aviso se descarta. La validación de los datos vive en {@link MaintenanceNoticePayload}.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceNoticePublisher {

    public static final String ROUTING_KEY = "habitacion.mantenimiento";
    public static final String EVENT_TYPE = "MAINTENANCE_NOTICE";

    private final OutboxEventPublisherPort outbox;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishScheduled(UUID maintenanceId, Room room, LocalDate startDate, LocalDate endDate) {
        enqueue(MaintenanceNoticePayload.scheduled(maintenanceId, room, startDate, endDate));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishUpdated(
            UUID maintenanceId,
            Room room,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate previousStartDate,
            LocalDate previousEndDate) {
        enqueue(MaintenanceNoticePayload.updated(
                maintenanceId, room, startDate, endDate, previousStartDate, previousEndDate));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCancelled(UUID maintenanceId, Room room, LocalDate startDate, LocalDate endDate) {
        enqueue(MaintenanceNoticePayload.cancelled(maintenanceId, room, startDate, endDate));
    }

    private void enqueue(MaintenanceNoticePayload payload) {
        outbox.enqueue(ROUTING_KEY, EVENT_TYPE, payload);
    }
}

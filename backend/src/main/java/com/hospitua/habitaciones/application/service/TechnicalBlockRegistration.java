package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto;
import com.hospitua.habitaciones.application.dto.TechnicalBlockCreatedDto;
import com.hospitua.habitaciones.application.ports.in.ConsultMaintenanceAvailabilityUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.MaintenanceConflictException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.MaintenanceConflict;
import com.hospitua.habitaciones.domain.model.MaintenanceDateRange;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transacción de la programación del bloqueo técnico (plan-programar-bloqueo-tecnico-para-habitacion.md T020, paso 3).
 * Es un bean aparte de {@link ScheduleTechnicalBlockService} porque una llamada interna a un método
 * {@code @Transactional} no pasa por el proxy de Spring. Bloquea la fila como primera carga (regla 13 del plan base), así
 * que los cruces locales se consultan sin que otra programación de la misma habitación se cuele (caso límite 2).
 */
@Component
@RequiredArgsConstructor
class TechnicalBlockRegistration {

    private final RoomRepositoryPort roomRepository;
    private final ConsultMaintenanceAvailabilityUseCase maintenanceAvailability;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;
    private final TechnicalBlockReportRepositoryPort reportRepository;
    private final MaintenanceNoticePublisher maintenanceNoticePublisher;
    private final Clock clock;

    /** Orden acordado con Módulo 2: reservas (ya consultadas), decidir, registrar y publicar. */
    @Transactional
    public TechnicalBlockCreatedDto register(
            UUID roomId, String memberId, String reason, MaintenanceDateRange range, LocalDate today) {
        Room room = roomRepository.lockById(roomId).orElseThrow(RoomNotFoundException::new);
        ScheduleTechnicalBlockService.ensureSchedulable(
                room.getStatus(), range.firstDay().equals(today));
        MaintenanceAvailabilityDto availability =
                maintenanceAvailability.consult(roomId, range.firstDay(), range.lastDay());
        if (!availability.available()) {
            throw new MaintenanceConflictException(availability.conflicts().stream()
                    .map(conflict -> new MaintenanceConflict(conflict.maintenanceStart(), conflict.maintenanceEnd()))
                    .toList());
        }

        TechnicalBlockReport report = TechnicalBlockReport.schedule(
                roomId, memberId, reason, range.firstDay(), range.lastDay(), OffsetDateTime.now(clock));
        if (report.covers(today)) {
            transitionRoomStateUseCase.transition(
                    roomId, RoomStatus.TechnicalBlock, memberId, SourceFlow.TECHNICAL_BLOCK, null);
            report.apply();
        }
        reportRepository.save(report);
        maintenanceNoticePublisher.publishScheduled(report.getId(), room, range.firstDay(), range.lastDay());
        return TechnicalBlockCreatedDto.of(report);
    }
}

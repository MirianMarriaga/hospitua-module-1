package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.DamageReportCreatedDto;
import com.hospitua.habitaciones.application.dto.ReportDamageCommand;
import com.hospitua.habitaciones.application.ports.in.ReportDamageUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marcar habitación inhabilitada por reparaciones (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T012). La
 * descripción se valida antes de tocar la base, así un texto inválido no bloquea la fila. Luego bloquea la habitación
 * como primera carga (regla 13 del plan base): de dos reportes simultáneos, el segundo espera y la encuentra en
 * {@code DisabledForRepairs}. Con propagación {@code REQUIRED} se une a la transacción del fin de limpieza con daño,
 * que ya tiene la fila bloqueada.
 */
@Service
@RequiredArgsConstructor
public class ReportDamageService implements ReportDamageUseCase {

    private final RoomRepositoryPort roomRepository;
    private final DamageReportRepositoryPort damageReportRepository;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;
    private final MaintenanceNoticePublisher maintenanceNoticePublisher;
    private final Clock clock;

    @Override
    @Transactional
    public DamageReportCreatedDto reportDamage(ReportDamageCommand command) {
        String description = DamageReport.normalizeDescription(command.damageDescription());
        Room room = roomRepository.lockById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        OffsetDateTime reportDateTime = transitionToDisabledForRepairs(room, command);
        DamageReport report = damageReportRepository.save(
                DamageReport.report(room.getId(), command.authorId(), description, reportDateTime));
        LocalDate reportDate = LocalDate.ofInstant(reportDateTime.toInstant(), clock.getZone());
        maintenanceNoticePublisher.publishScheduled(report.getId(), room, reportDate, reportDate);
        return new DamageReportCreatedDto(report.getId());
    }

    /** La marca del periodo abierto en el historial es también la del reporte (FR-008, FR-009). */
    private OffsetDateTime transitionToDisabledForRepairs(Room room, ReportDamageCommand command) {
        try {
            return transitionRoomStateUseCase.transition(
                    room.getId(), RoomStatus.DisabledForRepairs, command.authorId(), command.sourceFlow(), null);
        } catch (InvalidRoomTransitionException ex) {
            throw new InvalidRoomTransitionException(
                    ex.getCurrentStatus(),
                    "La habitación está " + ex.getCurrentStatus().displayName()
                            + "; ahora no se puede reportar un daño en ella.");
        }
    }
}

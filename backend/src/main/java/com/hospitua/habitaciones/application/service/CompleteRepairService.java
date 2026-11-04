package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.ports.in.CompleteRepairUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.application.service.LockedRepairTaskLoader.LockedRepairTask;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.ReparationTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmar fin de reparación (plan-confirmar-reparacion-finalizada.md T022). Con la habitación bloqueada: completa el
 * informe del bloqueo técnico si lo hay, pasa la habitación a {@code PendingCleaning} por el puerto transversal (que
 * escribe el historial), cierra la tarea con la marca de esa transición y deja el aviso a Módulo 2 en el outbox, todo en
 * la misma transacción.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteRepairService implements CompleteRepairUseCase {

    private final LockedRepairTaskLoader lockedRepairTaskLoader;
    private final ReparationTaskRepositoryPort reparationTaskRepository;
    private final TechnicalBlockReportRepositoryPort technicalBlockReportRepository;
    private final DamageReportRepositoryPort damageReportRepository;
    private final MarkPendingCleaningUseCase markPendingCleaningUseCase;
    private final MaintenanceNoticePublisher maintenanceNoticePublisher;
    private final Clock clock;

    @Override
    @Transactional
    public void completeRepair(UUID taskId, String memberId) {
        LockedRepairTask locked = lockedRepairTaskLoader.loadClosableBy(taskId, memberId);
        Room room = locked.room();
        // El estado antes de la transición decide el informe y el aviso
        boolean technicalBlock = room.getStatus() == RoomStatus.TechnicalBlock;
        Optional<TechnicalBlockReport> report = technicalBlock ? completeTechnicalBlockReport(room) : Optional.empty();
        OffsetDateTime pendingCleaningAt = markPendingCleaningUseCase
                .markPendingCleaning(
                        new MarkPendingCleaningCommand(room.getId(), SourceFlow.CONFIRM_REPAIR_END, memberId))
                .transitionDateTime();
        ReparationTask task = locked.task();
        task.close(ReparationTaskOutcome.Completed, pendingCleaningAt);
        reparationTaskRepository.save(task);
        LocalDate closingDate = LocalDate.ofInstant(pendingCleaningAt.toInstant(), clock.getZone());
        if (technicalBlock) {
            report.ifPresent(applied -> publishTechnicalBlockEnd(applied, room, closingDate));
        } else {
            publishRepairEnd(room, closingDate);
        }
    }

    private Optional<TechnicalBlockReport> completeTechnicalBlockReport(Room room) {
        Optional<TechnicalBlockReport> report = Optional.ofNullable(technicalBlockReportRepository
                .findAppliedByRoomIds(List.of(room.getId()))
                .get(room.getId()));
        report.ifPresentOrElse(
                applied -> {
                    applied.complete();
                    technicalBlockReportRepository.save(applied);
                },
                () -> log.warn(
                        "La habitación {} está en TechnicalBlock sin informe Applied: no hay informe que completar"
                                + " ni aviso que publicar",
                        room.getRoomNumber()));
        return report;
    }

    /** Si terminó en la fecha estimada, Módulo 2 ya tiene el rango correcto y no se avisa (FR-019). */
    private void publishTechnicalBlockEnd(TechnicalBlockReport report, Room room, LocalDate closingDate) {
        if (closingDate.equals(report.getEstimatedEndDate())) {
            return;
        }
        maintenanceNoticePublisher.publishUpdated(
                report.getId(),
                room,
                report.getStartDate(),
                closingDate,
                report.getStartDate(),
                report.getEstimatedEndDate());
    }

    private void publishRepairEnd(Room room, LocalDate closingDate) {
        damageReportRepository
                .findLatestByRoom(room.getId())
                .map(DamageReport::getId)
                .ifPresentOrElse(
                        damageReportId -> maintenanceNoticePublisher.publishCancelled(
                                damageReportId, room, closingDate, closingDate),
                        () -> log.warn(
                                "La habitación {} está en DisabledForRepairs sin reporte de daño: no hay aviso que"
                                        + " cancelar",
                                room.getRoomNumber()));
    }
}

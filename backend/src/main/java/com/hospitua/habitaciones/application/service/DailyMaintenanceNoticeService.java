package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.PublishDailyMaintenanceNoticesUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aviso diario de las 00:00 (plan-programar-bloqueo-tecnico-para-habitacion.md T025; FR-020). Una reparación o un
 * bloqueo técnico vencido no tienen fecha de fin conocida, así que cada día se le avisa a Módulo 2 que la habitación
 * no se puede asignar hoy. Solo lee y encola en el Outbox, sin bloquear filas; la transacción la exige
 * {@link MaintenanceNoticePublisher}. Separado de {@code runDaily}, que corre dos veces al día, para no avisar doble.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyMaintenanceNoticeService implements PublishDailyMaintenanceNoticesUseCase {

    private final RoomRepositoryPort roomRepository;
    private final TechnicalBlockReportRepositoryPort technicalBlockReportRepository;
    private final DamageReportRepositoryPort damageReportRepository;
    private final MaintenanceNoticePublisher maintenanceNoticePublisher;

    @Override
    @Transactional
    public void publishDaily(LocalDate today) {
        List<Room> rooms =
                roomRepository.findByStatusIn(EnumSet.of(RoomStatus.DisabledForRepairs, RoomStatus.TechnicalBlock));
        List<Room> disabled = withStatus(rooms, RoomStatus.DisabledForRepairs);
        List<Room> blocked = withStatus(rooms, RoomStatus.TechnicalBlock);

        Map<UUID, DamageReport> latestDamage = damageReportRepository.findLatestByRoomIds(
                disabled.stream().map(Room::getId).toList());
        for (Room room : disabled) {
            DamageReport report = latestDamage.get(room.getId());
            if (report == null) {
                log.warn("Habitación {} en DisabledForRepairs sin reporte de daño; no se envía su aviso", room.getId());
                continue;
            }
            maintenanceNoticePublisher.publishScheduled(report.getId(), room, today, today);
        }

        Map<UUID, TechnicalBlockReport> applied = technicalBlockReportRepository.findAppliedByRoomIds(
                blocked.stream().map(Room::getId).toList());
        for (Room room : blocked) {
            TechnicalBlockReport report = applied.get(room.getId());
            if (report == null) {
                log.warn("Habitación {} en TechnicalBlock sin informe Applied; no se envía su aviso", room.getId());
            } else if (report.isOverdue(today)) {
                maintenanceNoticePublisher.publishScheduled(report.getId(), room, today, today);
            }
        }
    }

    private static List<Room> withStatus(List<Room> rooms, RoomStatus status) {
        return rooms.stream().filter(room -> room.getStatus() == status).toList();
    }
}

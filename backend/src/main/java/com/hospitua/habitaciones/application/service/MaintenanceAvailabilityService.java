package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto;
import com.hospitua.habitaciones.application.ports.in.ConsultMaintenanceAvailabilityUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.MaintenanceConflict;
import com.hospitua.habitaciones.domain.model.MaintenanceDateRange;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultar Información de Mantenimientos (plan-consultar-informacion-mantenimientos.md T011). Valida los días antes
 * de leer la base (FR-009), verifica la habitación (FR-012) y responde los informes {@code Scheduled} o {@code Applied}
 * que se cruzan (FR-010). Un {@code Applied} vencido nunca se cruza, porque terminó antes de hoy y los días evaluados
 * empiezan hoy o después.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceAvailabilityService implements ConsultMaintenanceAvailabilityUseCase {

    private final RoomRepositoryPort roomRepository;
    private final TechnicalBlockReportRepositoryPort technicalBlockReportRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MaintenanceAvailabilityDto consult(UUID roomId, LocalDate firstDay, LocalDate lastDay) {
        LocalDate today = LocalDate.now(clock);
        MaintenanceDateRange range = MaintenanceDateRange.forAvailability(today, firstDay, lastDay);
        Room room = roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);

        List<MaintenanceConflict> conflicts = new ArrayList<>();
        if (range.includes(today) && unavailableToday(room, today)) {
            // Va primero: ningún otro cruce empieza antes de hoy dentro de los días evaluados
            conflicts.add(MaintenanceConflict.onlyOn(today));
        }
        technicalBlockReportRepository.findActiveOverlapping(roomId, range.firstDay(), range.lastDay()).stream()
                .map(MaintenanceConflict::of)
                .forEach(conflicts::add);
        return MaintenanceAvailabilityDto.of(roomId, conflicts);
    }

    /**
     * FR-014: la habitación no se puede asignar hoy si está en {@code DisabledForRepairs}, o en {@code TechnicalBlock}
     * con el informe vencido; con el informe vigente, su rango ya aparece como cruce. Un {@code TechnicalBlock} sin
     * informe {@code Applied} (datos inconsistentes: el código siempre los cambia juntos) también se reporta, para que
     * Módulo 2 no asigne una habitación bloqueada, y deja un aviso en el log para corregir el dato.
     */
    private boolean unavailableToday(Room room, LocalDate today) {
        if (room.getStatus() == RoomStatus.DisabledForRepairs) {
            return true;
        }
        if (room.getStatus() != RoomStatus.TechnicalBlock) {
            return false;
        }
        TechnicalBlockReport applied = technicalBlockReportRepository
                .findAppliedByRoomIds(List.of(room.getId()))
                .get(room.getId());
        if (applied == null) {
            log.warn(
                    "Habitación {} en TechnicalBlock sin informe Applied; se responde no disponible hoy", room.getId());
            return true;
        }
        return applied.isOverdue(today);
    }
}

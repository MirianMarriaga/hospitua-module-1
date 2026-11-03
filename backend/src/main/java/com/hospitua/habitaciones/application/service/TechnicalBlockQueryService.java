package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.TechnicalBlockViewDto;
import com.hospitua.habitaciones.application.ports.in.GetCurrentTechnicalBlockUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.TechnicalBlockNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Ver informe" del bloqueo técnico, de solo lectura (plan-programar-bloqueo-tecnico-para-habitacion.md T023; FR-009,
 * FR-010): el informe en curso ({@code Applied}) o, si no hay, el próximo programado ({@code Scheduled} de inicio más
 * temprano).
 */
@Service
@RequiredArgsConstructor
public class TechnicalBlockQueryService implements GetCurrentTechnicalBlockUseCase {

    private final RoomRepositoryPort roomRepository;
    private final TechnicalBlockReportRepositoryPort technicalBlockReportRepository;
    private final StaffAccountRepositoryPort staffAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public TechnicalBlockViewDto getCurrent(UUID roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);
        List<UUID> roomIds = List.of(roomId);
        TechnicalBlockReport report = Optional.ofNullable(technicalBlockReportRepository
                        .findAppliedByRoomIds(roomIds)
                        .get(roomId))
                .or(() -> Optional.ofNullable(technicalBlockReportRepository
                        .findEarliestScheduledByRoomIds(roomIds)
                        .get(roomId)))
                .orElseThrow(TechnicalBlockNotFoundException::new);
        return new TechnicalBlockViewDto(
                room.getRoomNumber(),
                report.getReason(),
                responsibleName(report.getMaintenanceStaffMemberId()),
                report.getStartDate(),
                report.getEstimatedEndDate(),
                report.getReportDateTime());
    }

    /** El nombre completo de la cuenta del responsable o, si la cuenta ya no existe, su {@code username}. */
    private String responsibleName(String memberId) {
        return staffAccountRepository
                .findByUsername(memberId)
                .map(StaffAccount::getFullName)
                .orElse(memberId);
    }
}

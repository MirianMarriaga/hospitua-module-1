package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.DamageReportViewDto;
import com.hospitua.habitaciones.application.ports.in.GetLatestDamageReportUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.DamageReportNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Ver informe", de solo lectura (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T013; FR-006, FR-007): solo
 * responde mientras la habitación está en {@code DisabledForRepairs}, con el último reporte, que es el que la inhabilitó.
 */
@Service
@RequiredArgsConstructor
public class DamageReportQueryService implements GetLatestDamageReportUseCase {

    private final RoomRepositoryPort roomRepository;
    private final DamageReportRepositoryPort damageReportRepository;
    private final StaffAccountRepositoryPort staffAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public DamageReportViewDto getLatest(UUID roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);
        if (room.getStatus() != RoomStatus.DisabledForRepairs) {
            throw new DamageReportNotFoundException(room.getStatus());
        }
        DamageReport report = damageReportRepository
                .findLatestByRoom(roomId)
                .orElseThrow(() -> new DamageReportNotFoundException(room.getStatus()));
        return new DamageReportViewDto(
                room.getRoomNumber(),
                report.getDamageDescription(),
                authorName(report.getAuthorId()),
                report.getReportDateTime());
    }

    /** El nombre completo de la cuenta del autor o, si la cuenta ya no existe, su {@code username} (T010). */
    private String authorName(String authorId) {
        return staffAccountRepository
                .findByUsername(authorId)
                .map(StaffAccount::getFullName)
                .orElse(authorId);
    }
}

package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.ApplyScheduledTechnicalBlocksUseCase;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Aplicar bloqueos técnicos programados (plan-programar-bloqueo-tecnico-para-habitacion.md T021; regla 10 del plan
 * base). {@code runDaily} no abre transacción: la caducidad es un solo {@code UPDATE} con la suya y cada habitación se
 * aplica en la suya, a través del proxy de {@link ScheduledTechnicalBlockApplier}. Es idempotente por estado: solo toca
 * informes {@code Scheduled}. No publica avisos (el aviso diario lo publica {@code DailyMaintenanceNoticeService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyScheduledTechnicalBlocksService implements ApplyScheduledTechnicalBlocksUseCase {

    private final TechnicalBlockReportRepositoryPort reportRepository;
    private final ScheduledTechnicalBlockApplier applier;

    @Override
    public void runDaily(LocalDate today) {
        int expired = reportRepository.expireScheduledEndedBefore(today);
        List<UUID> roomIds = reportRepository.findRoomIdsWithScheduledCovering(today);
        int applied = 0;
        for (UUID roomId : roomIds) {
            try {
                if (applier.apply(roomId, today)) {
                    applied++;
                }
            } catch (RuntimeException ex) {
                // Una habitación que falla no detiene las demás; su informe sigue Scheduled y se reintenta
                log.error("No se pudo aplicar el bloqueo técnico programado de la habitación {}", roomId, ex);
            }
        }
        log.info(
                "Bloqueos técnicos del {}: {} caducados, {} aplicados de {} habitaciones con programación vigente",
                today,
                expired,
                applied,
                roomIds.size());
    }

    @Override
    public boolean applyForRoom(UUID roomId, LocalDate today) {
        return applier.apply(roomId, today);
    }
}

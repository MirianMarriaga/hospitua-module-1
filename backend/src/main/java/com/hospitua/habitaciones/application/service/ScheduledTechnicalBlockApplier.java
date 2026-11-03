package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * La transacción de {@code applyForRoom} (plan-programar-bloqueo-tecnico-para-habitacion.md T021). Es un bean aparte
 * para que cada habitación de {@code runDaily} pase por el proxy de Spring y tenga su propia transacción; desde el fin de
 * limpieza se une a la transacción abierta y vuelve a bloquear la fila sin esperar (regla 13 del plan base).
 */
@Component
@RequiredArgsConstructor
class ScheduledTechnicalBlockApplier {

    private final RoomRepositoryPort roomRepository;
    private final TechnicalBlockReportRepositoryPort reportRepository;
    private final TransitionRoomStateUseCase transitionRoomStateUseCase;

    /**
     * Solo desde {@code Available} (FR-016; regla 6 de la máquina de estados): en otro estado, incluido {@code Inactive},
     * no hace nada y el informe sigue {@code Scheduled} para la siguiente ejecución. El periodo se registra con actor
     * nulo, porque lo aplica el sistema (FR-018).
     */
    @Transactional
    public boolean apply(UUID roomId, LocalDate today) {
        Optional<Room> room = roomRepository.lockById(roomId);
        if (room.isEmpty() || room.get().getStatus() != RoomStatus.Available) {
            return false;
        }
        Optional<TechnicalBlockReport> covering = reportRepository.findActiveOverlapping(roomId, today, today).stream()
                .filter(report -> report.getStatus() == TechnicalBlockStatus.Scheduled)
                .findFirst();
        if (covering.isEmpty()) {
            return false;
        }
        transitionRoomStateUseCase.transition(
                roomId, RoomStatus.TechnicalBlock, null, SourceFlow.TECHNICAL_BLOCK, null);
        TechnicalBlockReport report = covering.get();
        report.apply();
        reportRepository.save(report);
        return true;
    }
}

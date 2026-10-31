package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.CompleteCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.ReportDamageCommand;
import com.hospitua.habitaciones.application.ports.in.CompleteCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.in.ReportDamageUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmar fin de limpieza (plan-confirmar-fin-de-limpieza.md T016). No reimplementa transiciones: delega en los puertos
 * transversales, todos en esta transacción y con la habitación ya bloqueada. Prioridad tras {@code Available} (regla 6
 * de la máquina de estados): daño, luego llegada pendiente hoy. El mantenimiento programado que contiene hoy (FR-020)
 * se conecta con {@code applyForRoom} de plan-programar-bloqueo-tecnico-para-habitacion.md (T021).
 */
@Service
@RequiredArgsConstructor
public class CompleteCleaningService implements CompleteCleaningUseCase {

    private final LockedCleaningTaskLoader lockedCleaningTaskLoader;
    private final CleaningTaskRepositoryPort cleaningTaskRepository;
    private final DailyReservationRepositoryPort dailyReservationRepository;
    private final MarkRoomAvailableUseCase markRoomAvailableUseCase;
    private final MarkRoomReservedUseCase markRoomReservedUseCase;
    private final ReportDamageUseCase reportDamageUseCase;
    private final Clock clock;

    @Override
    @Transactional
    public void completeCleaning(CompleteCleaningCommand command) {
        CleaningTask task = lockedCleaningTaskLoader.loadClosableBy(command.taskId(), command.memberId());
        UUID roomId = task.getRoomId();
        OffsetDateTime availableAt = markRoomAvailableUseCase
                .markAvailable(
                        new MarkRoomAvailableCommand(roomId, SourceFlow.CONFIRM_CLEANING_END, command.memberId(), null))
                .transitionDateTime();
        CleaningTaskOutcome outcome;
        if (command.reportsDamage()) {
            // Con daño no se aparta aunque tenga llegada hoy: Recepción la ve con su alerta (FR-019)
            reportDamageUseCase.reportDamage(new ReportDamageCommand(
                    roomId, command.damageDescription(), command.memberId(), SourceFlow.CONFIRM_CLEANING_END));
            outcome = CleaningTaskOutcome.DamageReported;
        } else {
            reserveForPendingArrival(roomId, LocalDate.ofInstant(availableAt.toInstant(), clock.getZone()));
            outcome = CleaningTaskOutcome.Completed;
        }
        task.close(outcome, availableAt);
        cleaningTaskRepository.save(task);
    }

    /**
     * Con la habitación bloqueada y en {@code Available}, el apartado siempre resulta {@code RESERVED}; su resultado se
     * acepta sin revertir (FR-010).
     */
    private void reserveForPendingArrival(UUID roomId, LocalDate today) {
        Optional<String> reservationRef = dailyReservationRepository.findPendingArrivalReservationRef(roomId, today);
        reservationRef.ifPresent(ref -> markRoomReservedUseCase.markReserved(
                new MarkRoomReservedCommand(roomId, ref, ReservationTrigger.CLEANING_END)));
    }
}

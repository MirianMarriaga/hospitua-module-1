package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.ScheduleTechnicalBlockCommand;
import com.hospitua.habitaciones.application.dto.TechnicalBlockCreatedDto;
import com.hospitua.habitaciones.application.ports.in.CheckRoomReservationConflictsUseCase;
import com.hospitua.habitaciones.application.ports.in.ScheduleTechnicalBlockUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.ReservationConflictException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.MaintenanceDateRange;
import com.hospitua.habitaciones.domain.model.MaintenanceLimits;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Programar bloqueo técnico (plan-programar-bloqueo-tecnico-para-habitacion.md T020). Sin {@code @Transactional}: valida
 * la justificación, las fechas y el estado sin consultar nada externo, y consulta las reservas de Módulo 2 fuera de la
 * transacción, para no retener la fila bloqueada durante la llamada remota. Después {@link TechnicalBlockRegistration}
 * bloquea la fila, revalida, consulta los cruces locales, registra y publica el aviso.
 */
@Service
@RequiredArgsConstructor
public class ScheduleTechnicalBlockService implements ScheduleTechnicalBlockUseCase {

    static final String RESERVATION_CONFLICT =
            "La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas.";

    private final RoomRepositoryPort roomRepository;
    private final CheckRoomReservationConflictsUseCase reservationConflicts;
    private final TechnicalBlockRegistration registration;
    private final MaintenanceLimits limits;
    private final Clock clock;

    @Override
    public TechnicalBlockCreatedDto schedule(ScheduleTechnicalBlockCommand command) {
        String reason = TechnicalBlockReport.normalizeReason(command.reason());
        LocalDate today = LocalDate.now(clock);
        MaintenanceDateRange range =
                MaintenanceDateRange.forScheduling(today, limits, command.startDate(), command.estimatedEndDate());
        Room room = roomRepository.findById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        ensureSchedulable(room.getStatus(), range.firstDay().equals(today));

        ReservationConflictResult reservations =
                reservationConflicts.checkMaintenanceConflict(room.getId(), range.firstDay(), range.lastDay());
        if (reservations.hasConflict()) {
            throw new ReservationConflictException(RESERVATION_CONFLICT, reservations.reservations());
        }
        return registration.register(room.getId(), command.memberId(), reason, range, today);
    }

    /**
     * FR-001 y FR-006: nunca sobre una habitación {@code Inactive}; si el bloqueo inicia hoy, solo desde
     * {@code Available}. Con inicio futuro se acepta cualquier otro estado. La usa también la revalidación con la fila
     * bloqueada.
     */
    static void ensureSchedulable(RoomStatus status, boolean startsToday) {
        if (status == RoomStatus.Inactive) {
            throw new InvalidRoomTransitionException(
                    status, "La habitación está dada de baja; no se puede programar mantenimiento.");
        }
        if (startsToday && status != RoomStatus.Available) {
            throw new InvalidRoomTransitionException(
                    status,
                    "Hoy la habitación está " + status.displayName()
                            + ". Para empezar hoy debe estar disponible; elige una fecha de inicio posterior.");
        }
    }
}

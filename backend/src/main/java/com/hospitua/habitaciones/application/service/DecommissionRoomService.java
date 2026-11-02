package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.DecommissionRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDecommissionDto;
import com.hospitua.habitaciones.application.ports.in.CheckRoomReservationConflictsUseCase;
import com.hospitua.habitaciones.application.ports.in.DecommissionRoomUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.ReservationConflictException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dar de baja habitación (plan-dar-de-baja-habitacion.md T015). Sin {@code @Transactional}: la consulta a Módulo 2 va
 * fuera de la transacción, para no retenerla durante la llamada remota. Antes valida el motivo y que la habitación esté
 * {@code Available}, sin llamar a Módulo 2; después {@link RoomDecommissionRegistration} bloquea la fila, revalida y
 * registra la baja (regla 13 del plan base).
 */
@Service
@RequiredArgsConstructor
public class DecommissionRoomService implements DecommissionRoomUseCase {

    static final String RESERVATION_CONFLICT =
            "La habitación tiene reservas vigentes. Deben reasignarse antes de darla de baja.";

    private final RoomRepositoryPort roomRepository;
    private final CheckRoomReservationConflictsUseCase reservationConflicts;
    private final RoomDecommissionRegistration registration;

    @Override
    public RoomDecommissionDto decommission(DecommissionRoomCommand command) {
        String reasonDetail = RoomDecommission.normalizeDetail(command.reasonCategory(), command.reasonDetail());
        Room room = roomRepository.findById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        ensureAvailable(room);
        ReservationConflictResult conflicts = reservationConflicts.checkDecommissionConflict(room.getId());
        if (conflicts.hasConflict()) {
            throw new ReservationConflictException(RESERVATION_CONFLICT, conflicts.reservations());
        }
        return registration.register(command, reasonDetail);
    }

    /** FR-003: con un huésped activo el mensaje lo dice; en los demás estados nombra el estado actual. */
    private static void ensureAvailable(Room room) {
        RoomStatus status = room.getStatus();
        if (status == RoomStatus.Available) {
            return;
        }
        String message = status == RoomStatus.Occupied
                ? "La habitación tiene un huésped activo; debe estar Disponible para darla de baja."
                : "No es posible dar de baja la habitación: está en estado " + status.displayName()
                        + " y debe estar Disponible.";
        throw new InvalidRoomTransitionException(status, message);
    }
}

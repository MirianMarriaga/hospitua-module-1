package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.ReactivateRoomCommand;
import com.hospitua.habitaciones.application.dto.ReactivationResultDto;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.ReactivateRoomUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.UnknownDecommissionException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reactivar habitación (plan-marcar-habitacion-como-disponible.md T017). Bloquea la fila como primera carga (regla 13
 * del plan base) y recién después lee la baja: de dos reactivaciones simultáneas, la segunda espera y encuentra la baja
 * ya revertida. La transición y su periodo los hace {@link MarkRoomAvailableUseCase} con {@code MARK_AVAILABLE}.
 */
@Service
@RequiredArgsConstructor
public class ReactivateRoomService implements ReactivateRoomUseCase {

    private final RoomRepositoryPort roomRepository;
    private final RoomDecommissionRepositoryPort decommissionRepository;
    private final MarkRoomAvailableUseCase markRoomAvailableUseCase;

    @Override
    @Transactional
    public ReactivationResultDto reactivate(ReactivateRoomCommand command) {
        Room room = roomRepository.lockById(command.roomId()).orElseThrow(RoomNotFoundException::new);
        Optional<RoomDecommission> decommission = decommissionRepository
                .findById(command.decommissionId())
                .filter(found -> found.getRoomId().equals(room.getId()));
        if (decommission.isPresent() && decommission.get().isReactivated()) {
            return ReactivationResultDto.of(room, room.getStatus(), true);
        }
        RoomDecommissionQueryService.ensureInactive(room.getStatus());
        RoomDecommission current = decommission.orElseThrow(UnknownDecommissionException::new);
        OffsetDateTime reactivatedAt = markRoomAvailableUseCase
                .markAvailable(new MarkRoomAvailableCommand(
                        room.getId(), SourceFlow.MARK_AVAILABLE, command.administratorId(), null))
                .transitionDateTime();
        decommissionRepository.save(current.reactivate(command.administratorId(), reactivatedAt));
        return ReactivationResultDto.of(room, RoomStatus.Available, false);
    }
}

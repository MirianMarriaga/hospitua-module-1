package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.EditRoomCommand;
import com.hospitua.habitaciones.application.dto.EditRoomResultDto;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.EditRoomUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ConcurrentRoomUpdateException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAttributeChange;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editar habitación (plan-editar-habitacion.md T010, T015). Bloquea la fila (regla 13 del plan base) para que dos
 * ediciones con la misma {@code version} queden en fila: la segunda encuentra la versión nueva y se rechaza. El orden
 * de las validaciones es el del plan: versión, estado (antes de cualquier dato) y reglas de los atributos. La edición no
 * es una transición: no escribe en el historial, sino {@code ROOM_EDITED} en la bitácora.
 */
@Service
@RequiredArgsConstructor
public class EditRoomService implements EditRoomUseCase {

    /** Texto de la tabla de errores de la edición (caso borde "Habitación inexistente"). */
    static final String ROOM_NOT_FOUND = "No se encontró ninguna habitación con ese identificador.";

    private final RoomRepositoryPort roomRepository;
    private final RoomAuditLogRepositoryPort auditLog;

    @Override
    @Transactional
    public EditRoomResultDto edit(EditRoomCommand command) {
        Room room =
                roomRepository.lockById(command.roomId()).orElseThrow(() -> new RoomNotFoundException(ROOM_NOT_FOUND));
        if (command.version() == null || command.version() != room.getVersion()) {
            throw new ConcurrentRoomUpdateException();
        }
        ensureAvailable(room.getStatus());
        String currentNumber = room.getRoomNumber();
        List<RoomAttributeChange> changes = room.applyChanges(
                command.roomNumber(),
                command.floor(),
                command.categoryRoom(),
                command.maxCapacity(),
                command.baseRate());
        if (changes.isEmpty()) {
            return new EditRoomResultDto(false, changes, RoomDto.from(room));
        }
        if (!room.getRoomNumber().equals(currentNumber) && roomRepository.existsByRoomNumber(room.getRoomNumber())) {
            throw RoomNumberAlreadyExistsException.whenEditing(room.getRoomNumber());
        }
        Room saved = roomRepository.update(room);
        auditLog.record(new RoomAuditEntry(
                room.getId(), RoomAuditEventType.ROOM_EDITED, command.administratorId(), details(changes)));
        return new EditRoomResultDto(true, changes, RoomDto.from(saved));
    }

    /** FR-002 a FR-004: solo se edita una habitación {@code Available}, con el estado actual en el mensaje. */
    private static void ensureAvailable(RoomStatus status) {
        if (status != RoomStatus.Available) {
            throw new InvalidRoomTransitionException(
                    status,
                    "La habitación no está disponible para edición (estado actual: " + status.displayName() + ").");
        }
    }

    /** {@code details} del evento: los mismos cambios que ve el Administrador (FR-008). */
    private static Map<String, Object> details(List<RoomAttributeChange> changes) {
        List<Map<String, Object>> listed = changes.stream()
                .map(change -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("field", change.field());
                    entry.put("previousValue", change.previousValue());
                    entry.put("newValue", change.newValue());
                    return entry;
                })
                .toList();
        return Map.of("changes", listed);
    }
}

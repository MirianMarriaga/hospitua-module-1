package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra la habitación y abre su primer periodo en el historial en una sola transacción
 * (registrar-habitacion T011; FR-001 a FR-011, FR-013).
 */
@Service
@RequiredArgsConstructor
public class RegisterRoomService implements RegisterRoomUseCase {

    private final RoomRepositoryPort roomRepository;
    private final TransitionRoomStateUseCase transitionRoomState;

    @Override
    @Transactional
    public RoomDto register(RegisterRoomCommand command) {
        Room room = Room.register(
                command.roomNumber(),
                command.floor(),
                command.categoryRoom(),
                command.maxCapacity(),
                command.baseRate());

        if (roomRepository.existsByRoomNumber(room.getRoomNumber())) {
            throw new RoomNumberAlreadyExistsException(room.getRoomNumber());
        }

        Room saved = roomRepository.insert(room);
        transitionRoomState.registerInitialStatus(saved.getId(), command.administratorId(), SourceFlow.REGISTER_ROOM);
        return RoomDto.from(saved);
    }
}

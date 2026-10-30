package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.Module2RoomDto;
import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomInventoryUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventario de habitaciones, sin escrituras (consultar-inventario T015; FR-003 a FR-006, FR-009 a FR-011). Las
 * vistas ven todas las habitaciones; Módulo 2, solo las vendibles.
 */
@Service
@RequiredArgsConstructor
public class RoomInventoryQueryService implements ConsultRoomInventoryUseCase {

    private final RoomRepositoryPort roomRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedResultDto<RoomDto> search(RoomInventoryFilter filter, int page, int size) {
        return roomRepository.searchInventory(filter, page, size).map(RoomDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDto getById(UUID roomId) {
        return roomRepository.findById(roomId).map(RoomDto::from).orElseThrow(RoomNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Module2RoomDto> listSellableByCategory(RoomCategory categoryRoom) {
        return roomRepository.findSellableByCategory(categoryRoom).stream()
                .map(Module2RoomDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Module2RoomDto getSellableById(UUID roomId) {
        return roomRepository
                .findSellableById(roomId)
                .map(Module2RoomDto::from)
                .orElseThrow(RoomNotFoundException::new);
    }
}

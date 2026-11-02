package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.RoomBaseRateResponseDto;
import com.hospitua.habitaciones.application.ports.in.GetRoomBaseRateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultar tarifa base (plan-consultar-tarifa-base.md T008, T013). Solo lectura y sin bloqueo: responde en cualquiera
 * de los 8 estados, incluido {@code Inactive}, porque toda habitación tiene tarifa (FR-005, FR-006).
 */
@Service
@RequiredArgsConstructor
public class RoomBaseRateQueryService implements GetRoomBaseRateUseCase {

    private final RoomRepositoryPort roomRepository;

    @Override
    @Transactional(readOnly = true)
    public RoomBaseRateResponseDto getBaseRate(UUID roomId, LocalDate date) {
        return roomRepository
                .findById(roomId)
                .map(room -> RoomBaseRateResponseDto.of(room, date))
                .orElseThrow(RoomNotFoundException::new);
    }
}

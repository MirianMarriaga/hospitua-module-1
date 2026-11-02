package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.RoomBaseRateResponseDto;
import java.time.LocalDate;
import java.util.UUID;

/** Tarifa base de una habitación para Módulo 3; la fecha no la cambia (plan-consultar-tarifa-base.md, FR-002). */
public interface GetRoomBaseRateUseCase {

    RoomBaseRateResponseDto getBaseRate(UUID roomId, LocalDate date);
}

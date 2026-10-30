package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomStateHistoryUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RoomStateHistoryQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador REST del historial de estados, de solo lectura (plan-consultar-historial-de-estados.md, T011). */
@RestController
@RequestMapping("/api/room-state-history")
@RequiredArgsConstructor
public class RoomStateHistoryController {

    private final ConsultRoomStateHistoryUseCase consultRoomStateHistoryUseCase;

    /** Periodos que cumplen los filtros, en orden cronológico y de 15 en 15 (FR-001, FR-008). */
    @GetMapping
    public PagedResultDto<RoomStateHistoryRowDto> search(@Valid @ModelAttribute RoomStateHistoryQuery query) {
        return consultRoomStateHistoryUseCase.search(query.toFilter(), query.pageOrDefault());
    }
}

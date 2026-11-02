package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.RoomBaseRateResponseDto;
import com.hospitua.habitaciones.application.ports.in.GetRoomBaseRateUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RoomBaseRateQuery;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integración Módulo 3 → Módulo 1 (plan-consultar-tarifa-base.md T009): tarifa base de una habitación para la
 * tarifación. Sin {@code X-User}: lo consume otro módulo, no el personal.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/base-rate")
@RequiredArgsConstructor
public class RoomBaseRateController {

    private final GetRoomBaseRateUseCase getRoomBaseRateUseCase;

    @GetMapping
    public RoomBaseRateResponseDto baseRate(@PathVariable UUID roomId, @Valid @ModelAttribute RoomBaseRateQuery query) {
        return getRoomBaseRateUseCase.getBaseRate(roomId, query.toDate());
    }
}

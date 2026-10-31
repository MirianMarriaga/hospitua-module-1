package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.SettlementResponseDto;
import com.hospitua.habitaciones.application.ports.in.QuerySettlementUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liquidación para los pasos 2 y 3 del Check-Out (consultar-liquidacion Endpoint 2, T014, T025). */
@RestController
@RequestMapping("/api/stays")
@RequiredArgsConstructor
public class SettlementController {

    private final QuerySettlementUseCase querySettlementUseCase;

    /** Siempre 200 si la estancia está activa, también con Módulo 3 caído ({@code UNAVAILABLE}); nunca se guarda en caché. */
    @GetMapping("/{stayId}/settlement")
    public ResponseEntity<SettlementResponseDto> settlement(@PathVariable UUID stayId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(querySettlementUseCase.querySettlement(stayId));
    }
}

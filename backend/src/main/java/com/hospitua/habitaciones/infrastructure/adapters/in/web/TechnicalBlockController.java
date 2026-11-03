package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.TechnicalBlockCreatedDto;
import com.hospitua.habitaciones.application.ports.in.ScheduleTechnicalBlockUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.ScheduleTechnicalBlockRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bloqueos técnicos (plan-programar-bloqueo-tecnico-para-habitacion.md T023): "Programar mantenimiento" del panel de
 * mantenimiento. "Ver informe" ({@code GET …/current}) llega con L26.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/technical-blocks")
@RequiredArgsConstructor
public class TechnicalBlockController {

    private final ScheduleTechnicalBlockUseCase scheduleTechnicalBlockUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** El responsable es el usuario actual; responde 201 con el informe y su estado (Scheduled o Applied). */
    @PostMapping
    public ResponseEntity<TechnicalBlockCreatedDto> schedule(
            @PathVariable UUID roomId,
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody ScheduleTechnicalBlockRequest request) {
        String memberId = currentUserResolver.resolve(xUser);
        TechnicalBlockCreatedDto created = scheduleTechnicalBlockUseCase.schedule(request.toCommand(roomId, memberId));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

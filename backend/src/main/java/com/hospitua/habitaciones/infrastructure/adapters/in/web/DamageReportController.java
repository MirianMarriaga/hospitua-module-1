package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.DamageReportCreatedDto;
import com.hospitua.habitaciones.application.dto.DamageReportViewDto;
import com.hospitua.habitaciones.application.ports.in.GetLatestDamageReportUseCase;
import com.hospitua.habitaciones.application.ports.in.ReportDamageUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.ReportDamageRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reportes de daño (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T013): "Reportar daño" de los paneles de
 * limpieza y mantenimiento, y "Ver informe" del panel de mantenimiento.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/damage-reports")
@RequiredArgsConstructor
public class DamageReportController {

    private final ReportDamageUseCase reportDamageUseCase;
    private final GetLatestDamageReportUseCase getLatestDamageReportUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** Reportar daño: el autor es el usuario actual; responde 201 con el reporte creado. */
    @PostMapping
    public ResponseEntity<DamageReportCreatedDto> reportDamage(
            @PathVariable UUID roomId,
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody ReportDamageRequest request) {
        String authorId = currentUserResolver.resolve(xUser);
        DamageReportCreatedDto report = reportDamageUseCase.reportDamage(request.toCommand(roomId, authorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    /** Ver informe: el reporte que originó el {@code DisabledForRepairs} actual (FR-006). */
    @GetMapping("/latest")
    public DamageReportViewDto latest(@PathVariable UUID roomId) {
        return getLatestDamageReportUseCase.getLatest(roomId);
    }
}

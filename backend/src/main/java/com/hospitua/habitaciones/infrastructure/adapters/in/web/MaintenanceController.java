package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.MaintenancePanelDto;
import com.hospitua.habitaciones.application.dto.RepairTaskStartedDto;
import com.hospitua.habitaciones.application.ports.in.CompleteRepairUseCase;
import com.hospitua.habitaciones.application.ports.in.GetMaintenancePanelUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseRepairTaskUseCase;
import com.hospitua.habitaciones.application.ports.in.StartRepairUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.StartRepairRequest;
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
 * Endpoints del Personal de mantenimiento bajo {@code /api/maintenance} (plan-confirmar-reparacion-finalizada.md T004,
 * T011 y T024; plan-consultar-panel-mantenimiento.md T009).
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final GetMaintenancePanelUseCase getMaintenancePanelUseCase;
    private final StartRepairUseCase startRepairUseCase;
    private final CompleteRepairUseCase completeRepairUseCase;
    private final ReleaseRepairTaskUseCase releaseRepairTaskUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** El panel del usuario actual, o su tarea activa para que el frontend lo redirija (FR-001). */
    @GetMapping("/panel")
    public MaintenancePanelDto panel(@RequestHeader(value = "X-User", required = false) String xUser) {
        return getMaintenancePanelUseCase.getPanel(currentUserResolver.resolve(xUser));
    }

    /** Iniciar reparaciones: el miembro es el usuario actual; responde 201 con la tarea creada (FR-011, FR-012). */
    @PostMapping("/tasks")
    public ResponseEntity<RepairTaskStartedDto> startRepair(
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody StartRepairRequest request) {
        String memberId = currentUserResolver.resolve(xUser);
        RepairTaskStartedDto task = startRepairUseCase.startRepair(request.toCommand(memberId));
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /** Confirmar fin: la habitación pasa a pendiente de limpieza; responde 204 y el frontend vuelve al panel. */
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> completeRepair(
            @PathVariable UUID taskId, @RequestHeader(value = "X-User", required = false) String xUser) {
        completeRepairUseCase.completeRepair(taskId, currentUserResolver.resolve(xUser));
        return ResponseEntity.noContent().build();
    }

    /** Liberar tarea: la habitación no cambia, para que otro miembro la retome; responde 204. */
    @PostMapping("/tasks/{taskId}/release")
    public ResponseEntity<Void> releaseTask(
            @PathVariable UUID taskId, @RequestHeader(value = "X-User", required = false) String xUser) {
        releaseRepairTaskUseCase.releaseTask(taskId, currentUserResolver.resolve(xUser));
        return ResponseEntity.noContent().build();
    }
}

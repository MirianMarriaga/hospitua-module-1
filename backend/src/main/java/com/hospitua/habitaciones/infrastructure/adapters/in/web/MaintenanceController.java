package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.MaintenancePanelDto;
import com.hospitua.habitaciones.application.ports.in.GetMaintenancePanelUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del Personal de mantenimiento bajo {@code /api/maintenance} (plan-confirmar-reparacion-finalizada.md T004;
 * plan-consultar-panel-mantenimiento.md T009). Las tareas de reparación le agregan los suyos.
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final GetMaintenancePanelUseCase getMaintenancePanelUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** El panel del usuario actual, o su tarea activa para que el frontend lo redirija (FR-001). */
    @GetMapping("/panel")
    public MaintenancePanelDto panel(@RequestHeader(value = "X-User", required = false) String xUser) {
        return getMaintenancePanelUseCase.getPanel(currentUserResolver.resolve(xUser));
    }
}

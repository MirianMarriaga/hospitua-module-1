package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del Personal de mantenimiento bajo {@code /api/maintenance} (plan-confirmar-reparacion-finalizada.md T004).
 * Se crea sin endpoints: el panel (plan-consultar-panel-mantenimiento.md) y las tareas de reparación
 * (plan-confirmar-reparacion-finalizada.md) le agregan los suyos.
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {}

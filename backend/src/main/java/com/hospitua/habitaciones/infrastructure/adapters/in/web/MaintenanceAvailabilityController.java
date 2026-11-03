package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto;
import com.hospitua.habitaciones.application.ports.in.ConsultMaintenanceAvailabilityUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.MaintenanceAvailabilityQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integración Módulo 2 → Módulo 1 (plan-consultar-informacion-mantenimientos.md T012): disponibilidad de una habitación
 * frente a los mantenimientos para una estadía. Sin {@code X-User}: lo consume otro módulo.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/maintenances")
@RequiredArgsConstructor
public class MaintenanceAvailabilityController {

    private final ConsultMaintenanceAvailabilityUseCase consultMaintenanceAvailability;

    @GetMapping
    public MaintenanceAvailabilityDto consult(@Valid @ModelAttribute MaintenanceAvailabilityQuery query) {
        return consultMaintenanceAvailability.consult(query.toRoomId(), query.firstDay(), query.lastDay());
    }
}

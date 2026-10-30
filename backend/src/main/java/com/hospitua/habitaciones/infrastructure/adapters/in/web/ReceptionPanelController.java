package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.DeparturesDto;
import com.hospitua.habitaciones.application.dto.PendingArrivalsDto;
import com.hospitua.habitaciones.application.ports.in.SearchDailyReservationsUseCase;
import com.hospitua.habitaciones.application.ports.in.SearchDeparturesUseCase;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de Recepción, de solo lectura y sin llamadas a Módulo 2 (plan-consultar-panel-recepcion.md, T012). La búsqueda
 * de cada pestaña se limita a 100 caracteres para acotar las comparaciones sobre los datos locales (400
 * VALIDATION_ERROR).
 */
@Validated
@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionPanelController {

    private final SearchDailyReservationsUseCase searchDailyReservationsUseCase;
    private final SearchDeparturesUseCase searchDeparturesUseCase;

    /** Pestaña Llegadas: habitaciones de las reservas de hoy pendientes de check-in, agrupadas por reserva (FR-003). */
    @GetMapping("/arrivals")
    public PendingArrivalsDto arrivals(
            @RequestParam(required = false) @Size(max = 100, message = "La búsqueda admite máximo 100 caracteres.")
                    String query) {
        return searchDailyReservationsUseCase.searchPendingArrivals(query);
    }

    /** Pestaña Salidas: estancias activas con salida hoy o vencida, primero las vencidas (FR-004, FR-007). */
    @GetMapping("/departures")
    public DeparturesDto departures(
            @RequestParam(required = false) @Size(max = 100, message = "La búsqueda admite máximo 100 caracteres.")
                    String query) {
        return searchDeparturesUseCase.searchDepartures(query);
    }
}

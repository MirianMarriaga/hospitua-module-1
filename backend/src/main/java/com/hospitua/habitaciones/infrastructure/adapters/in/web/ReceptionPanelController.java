package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.PendingArrivalsDto;
import com.hospitua.habitaciones.application.ports.in.SearchDailyReservationsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de Recepción, de solo lectura y sin llamadas a Módulo 2 (plan-consultar-panel-recepcion.md, T012). Sin
 * errores propios: un fallo inesperado responde con el formato {@code ApiError} del plan base.
 */
@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionPanelController {

    private final SearchDailyReservationsUseCase searchDailyReservationsUseCase;

    /** Pestaña Llegadas: habitaciones de las reservas de hoy pendientes de check-in, agrupadas por reserva (FR-003). */
    @GetMapping("/arrivals")
    public PendingArrivalsDto arrivals(@RequestParam(required = false) String query) {
        return searchDailyReservationsUseCase.searchPendingArrivals(query);
    }
}

package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.DeparturesDto;

/**
 * Salidas del día y vencidas desde las estancias locales, sin llamar a Módulo 2 (plan-consultar-panel-recepcion.md
 * T017, FR-004).
 */
public interface SearchDeparturesUseCase {

    /**
     * Estancias activas con salida esperada hoy o antes, primero las vencidas. Un {@code query} nulo o en blanco
     * devuelve todas; si no, filtra sin distinguir mayúsculas ni espacios sobrantes por {@code reservationRef},
     * documento del titular o número de habitación (exactos) o por parte del nombre o el apellido del titular.
     */
    DeparturesDto searchDepartures(String query);
}

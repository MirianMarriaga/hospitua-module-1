package com.hospitua.habitaciones.application.service;

import java.util.Locale;

/** Texto de los buscadores del panel de Recepción (plan-consultar-reservas.md T030; panel-recepcion FR-003, FR-004). */
final class SearchQuery {

    private SearchQuery() {}

    /** Sin espacios sobrantes y en minúsculas; nulo si no queda texto, para listar todo. */
    static String normalize(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

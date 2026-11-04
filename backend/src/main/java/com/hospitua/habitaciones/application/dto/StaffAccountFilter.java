package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.StaffArea;

/**
 * Filtros de la lista del personal (plan-gestionar-personal.md T005; FR-007). Cada uno es opcional ({@code null} = sin
 * filtro) y se combinan: {@code query} busca por coincidencia parcial en usuario o nombre, sin distinguir mayúsculas ni
 * tildes.
 */
public record StaffAccountFilter(StaffArea area, Boolean active, String query) {}

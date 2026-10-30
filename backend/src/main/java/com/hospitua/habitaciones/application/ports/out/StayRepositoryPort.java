package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.DepartureSummary;
import com.hospitua.habitaciones.domain.model.Stay;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia de estancias de huéspedes.
 */
public interface StayRepositoryPort {

    Stay save(Stay stay);

    Optional<Stay> findById(UUID id);

    /**
     * Estancias activas (sin {@code checkOutDate}) con salida esperada en {@code today} o antes, primero las vencidas y
     * después por número de habitación (panel-recepcion T006, FR-004). {@code normalizedQuery} va sin espacios sobrantes
     * y en minúsculas; nulo devuelve todas, y si no, filtra por referencia, documento o número de habitación exactos o
     * por parte del nombre, del apellido o del nombre completo del titular.
     */
    List<DepartureSummary> searchActiveDepartures(LocalDate today, String normalizedQuery);

    /** Estancias activas con salida esperada en {@code date} (KPI "Salidas de hoy"). */
    long countActiveWithExpectedCheckoutOn(LocalDate date);

    /** Estancias activas con salida esperada antes de {@code date} (KPI "Salidas vencidas"). */
    long countActiveWithExpectedCheckoutBefore(LocalDate date);
}

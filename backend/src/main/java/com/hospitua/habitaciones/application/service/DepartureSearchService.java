package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.DeparturesDto;
import com.hospitua.habitaciones.application.ports.in.SearchDeparturesUseCase;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pestaña Salidas desde las estancias locales ({@code Stay}, {@code Room} y {@code RoomGuest}), sin llamar a Módulo 2
 * (plan-consultar-panel-recepcion.md T017; FR-004, FR-007). "Hoy" sale del reloj de Bogotá.
 */
@Service
@RequiredArgsConstructor
public class DepartureSearchService implements SearchDeparturesUseCase {

    private final StayRepositoryPort stayRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DeparturesDto searchDepartures(String query) {
        LocalDate today = LocalDate.now(clock);
        return new DeparturesDto(stayRepository.searchActiveDepartures(today, SearchQuery.normalize(query)));
    }
}

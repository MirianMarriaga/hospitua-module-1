package com.hospitua.habitaciones.application.service;

import static org.mockito.Mockito.verify;

import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartureSearchServiceTest {

    // 23:30 del 9-10 en Bogotá: en UTC ya es 10-10, así que "hoy" debe salir del reloj de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Mock
    private StayRepositoryPort stayRepository;

    private DepartureSearchService service;

    @BeforeEach
    void setUp() {
        service = new DepartureSearchService(stayRepository, CLOCK);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("T014: una búsqueda nula o en blanco lista todas las salidas de hoy (Bogotá) y vencidas")
    void blankQuery_listsAll(String query) {
        service.searchDepartures(query);

        verify(stayRepository).searchActiveDepartures(TODAY, null);
    }

    @Test
    @DisplayName("T017: la búsqueda va sin espacios sobrantes y en minúsculas")
    void query_isTrimmedAndLowercased() {
        service.searchDepartures("  Carlos   PÉREZ ");

        verify(stayRepository).searchActiveDepartures(TODAY, "carlos pérez");
    }
}

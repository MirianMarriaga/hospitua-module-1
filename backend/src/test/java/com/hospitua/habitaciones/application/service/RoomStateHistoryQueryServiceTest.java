package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.application.ports.out.RoomStateHistoryRepositoryPort;
import com.hospitua.habitaciones.domain.exception.DomainError;
import com.hospitua.habitaciones.domain.exception.InvalidDateRangeException;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Consulta del historial de estados (plan-consultar-historial-de-estados.md T003 a T007). */
@ExtendWith(MockitoExtension.class)
class RoomStateHistoryQueryServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-14T09:15:00-05:00");

    @Mock
    private RoomStateHistoryRepositoryPort historyRepository;

    private RoomStateHistoryQueryService service;

    @BeforeEach
    void setUp() {
        service = new RoomStateHistoryQueryService(historyRepository);
    }

    @Test
    @DisplayName("T003: devuelve los periodos en el orden del repositorio con todos sus datos; la transición autónoma"
            + " llega sin responsable")
    void search_returnsChronologicalRows() {
        RoomStateHistoryRowDto registered =
                row(RoomStatus.Available, T0, T0.plusHours(1), "Laura Méndez", SourceFlow.REGISTER_ROOM);
        RoomStateHistoryRowDto autonomous =
                row(RoomStatus.PendingCleaning, T0.plusHours(1), null, null, SourceFlow.CHECK_OUT);
        RoomStateHistoryFilter filter = new RoomStateHistoryFilter(ROOM_ID, null, null, null, null, null);
        when(historyRepository.search(filter, 1))
                .thenReturn(new PagedResultDto<>(List.of(registered, autonomous), 1, 15, 2, 1));

        PagedResultDto<RoomStateHistoryRowDto> page = service.search(filter, 1);

        assertThat(page.items()).containsExactly(registered, autonomous);
        assertThat(page.items().getFirst().roomNumber()).isEqualTo("203");
        assertThat(page.items().getFirst().categoryRoom()).isEqualTo(RoomCategory.DOBLE);
        assertThat(page.items().getFirst().sourceFlowName()).isEqualTo("Registrar habitación");
        assertThat(page.items().get(1).actorName()).isNull();
    }

    @Test
    @DisplayName("T004: los filtros combinados llegan al repositorio, con el número normalizado")
    void search_passesCombinedFilters() {
        RoomStateHistoryFilter filter = new RoomStateHistoryFilter(
                null,
                " 203a ",
                RoomCategory.DOBLE,
                RoomStatus.InCleaning,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31));
        when(historyRepository.search(filter, 1)).thenReturn(new PagedResultDto<>(List.of(), 1, 15, 0, 0));

        assertThat(service.search(filter, 1).items()).isEmpty();
        assertThat(filter.roomNumber()).isEqualTo("203A");
        verify(historyRepository).search(filter, 1);
    }

    @Test
    @DisplayName("T005: un periodo abierto llega con endDateTime nulo")
    void search_openPeriod_hasNullEnd() {
        RoomStateHistoryRowDto open = row(RoomStatus.Available, T0, null, "Laura Méndez", SourceFlow.REGISTER_ROOM);
        when(historyRepository.search(RoomStateHistoryFilter.none(), 1))
                .thenReturn(new PagedResultDto<>(List.of(open), 1, 15, 1, 1));

        assertThat(service.search(RoomStateHistoryFilter.none(), 1).items())
                .singleElement()
                .extracting(RoomStateHistoryRowDto::endDateTime)
                .isNull();
    }

    @Test
    @DisplayName("T006: \"Desde\" posterior a \"Hasta\" se rechaza con INVALID_DATE_RANGE sin consultar")
    void search_fromAfterTo_rejectedWithoutQuery() {
        RoomStateHistoryFilter filter =
                new RoomStateHistoryFilter(null, null, null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31));

        assertThatThrownBy(() -> service.search(filter, 1))
                .isInstanceOfSatisfying(InvalidDateRangeException.class, ex -> {
                    assertThat(ex.error()).isEqualTo(DomainError.INVALID_DATE_RANGE);
                    assertThat(ex.details()).isEqualTo(Map.of("rule", "FROM_AFTER_TO"));
                    assertThat(ex.getMessage()).isEqualTo("La fecha \"Desde\" no puede ser posterior a \"Hasta\".");
                });
        verifyNoInteractions(historyRepository);
    }

    @Test
    @DisplayName("T006: un rango de un solo día es válido")
    void search_sameDay_isValid() {
        LocalDate day = LocalDate.of(2026, 1, 14);
        RoomStateHistoryFilter filter = new RoomStateHistoryFilter(null, null, null, null, day, day);
        when(historyRepository.search(filter, 1)).thenReturn(new PagedResultDto<>(List.of(), 1, 15, 0, 0));

        assertThat(service.search(filter, 1).totalElements()).isZero();
    }

    @Test
    @DisplayName("T007: la página pedida llega al repositorio y se devuelve con sus totales, de 15 en 15")
    void search_returnsPageWithTotals() {
        when(historyRepository.search(RoomStateHistoryFilter.none(), 3))
                .thenReturn(new PagedResultDto<>(List.of(), 3, 15, 42, 3));

        PagedResultDto<RoomStateHistoryRowDto> page = service.search(RoomStateHistoryFilter.none(), 3);

        assertThat(RoomStateHistoryRepositoryPort.PAGE_SIZE).isEqualTo(15);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(15);
        assertThat(page.totalElements()).isEqualTo(42);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    private static RoomStateHistoryRowDto row(
            RoomStatus status, OffsetDateTime start, OffsetDateTime end, String actorName, SourceFlow sourceFlow) {
        return new RoomStateHistoryRowDto(
                ROOM_ID, "203", RoomCategory.DOBLE, status, start, end, actorName, sourceFlow);
    }
}

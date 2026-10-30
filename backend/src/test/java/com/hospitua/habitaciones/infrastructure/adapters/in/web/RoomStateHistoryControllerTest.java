package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomStateHistoryUseCase;
import com.hospitua.habitaciones.domain.exception.InvalidDateRangeException;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Pruebas MockMvc de GET /api/room-state-history con GlobalExceptionHandler (consultar-historial T011). */
class RoomStateHistoryControllerTest {

    private static final UUID ROOM_ID = UUID.fromString("9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182");

    private MockMvc mockMvc;
    private ConsultRoomStateHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = Mockito.mock(ConsultRoomStateHistoryUseCase.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T15:05:00Z"), ZoneId.of("America/Bogota"));

        mockMvc = MockMvcBuilders.standaloneSetup(new RoomStateHistoryController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorFactory(clock)))
                .build();
    }

    @Test
    @DisplayName("sin parámetros pide la página 1 sin filtros y devuelve los periodos con sus totales")
    void search_defaults() throws Exception {
        RoomStateHistoryRowDto row = new RoomStateHistoryRowDto(
                ROOM_ID,
                "203",
                RoomCategory.DOBLE,
                RoomStatus.InCleaning,
                OffsetDateTime.parse("2026-01-14T09:15:00-05:00"),
                null,
                null,
                SourceFlow.START_CLEANING);
        when(useCase.search(RoomStateHistoryFilter.none(), 1))
                .thenReturn(new PagedResultDto<>(List.of(row), 1, 15, 1, 1));

        mockMvc.perform(get("/api/room-state-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.items[0].roomNumber").value("203"))
                .andExpect(jsonPath("$.items[0].categoryRoom").value("DOBLE"))
                .andExpect(jsonPath("$.items[0].status").value("InCleaning"))
                .andExpect(jsonPath("$.items[0].endDateTime").isEmpty())
                .andExpect(jsonPath("$.items[0].actorName").isEmpty())
                .andExpect(jsonPath("$.items[0].sourceFlow").value("START_CLEANING"))
                .andExpect(jsonPath("$.items[0].sourceFlowName").value("Marcar habitación en limpieza"))
                .andExpect(jsonPath("$.size").value(15))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("todos los filtros llegan combinados al caso de uso")
    void search_allFilters() throws Exception {
        RoomStateHistoryFilter filter = new RoomStateHistoryFilter(
                ROOM_ID,
                "203",
                RoomCategory.DOBLE,
                RoomStatus.InCleaning,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31));
        when(useCase.search(filter, 2)).thenReturn(new PagedResultDto<>(List.of(), 2, 15, 0, 0));

        mockMvc.perform(get("/api/room-state-history")
                        .param("roomId", ROOM_ID.toString())
                        .param("roomNumber", "203")
                        .param("categoryRoom", "DOBLE")
                        .param("status", "InCleaning")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    @DisplayName("esc. 4: \"Desde\" posterior a \"Hasta\" responde 400 INVALID_DATE_RANGE con la regla")
    void search_fromAfterTo_returns400() throws Exception {
        when(useCase.search(any(), anyInt())).thenThrow(InvalidDateRangeException.fromAfterTo());

        mockMvc.perform(get("/api/room-state-history")
                        .param("from", "2026-02-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.message").value("La fecha \"Desde\" no puede ser posterior a \"Hasta\"."))
                .andExpect(jsonPath("$.details.rule").value("FROM_AFTER_TO"));
    }

    @ParameterizedTest
    @CsvSource({
        "roomId, 203",
        "categoryRoom, PENTHOUSE",
        "status, Ocupada",
        "from, 14/01/2026",
        "to, 2026-13-01",
        "page, 0",
        "page, uno"
    })
    @DisplayName("un parámetro fuera de sus valores responde 400 VALIDATION_ERROR con el campo, sin consultar")
    void search_invalidParameter_returns400(String field, String value) throws Exception {
        mockMvc.perform(get("/api/room-state-history").param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value(field));

        verify(useCase, never()).search(any(), anyInt());
    }
}

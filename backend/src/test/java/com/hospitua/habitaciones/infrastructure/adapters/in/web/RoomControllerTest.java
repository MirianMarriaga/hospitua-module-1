package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.application.dto.Module2RoomDto;
import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.application.dto.RoomInventorySort;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomInventoryUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.domain.exception.CurrentUserRequiredException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Pruebas MockMvc de POST /api/rooms con GlobalExceptionHandler (plan-registrar-habitacion.md). */
class RoomControllerTest {

    private static final UUID ROOM_ID = UUID.fromString("9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182");

    private MockMvc mockMvc;
    private RegisterRoomUseCase registerRoomUseCase;
    private ConsultRoomInventoryUseCase consultRoomInventoryUseCase;
    private CurrentUserResolver currentUserResolver;

    @BeforeEach
    void setUp() {
        registerRoomUseCase = Mockito.mock(RegisterRoomUseCase.class);
        consultRoomInventoryUseCase = Mockito.mock(ConsultRoomInventoryUseCase.class);
        currentUserResolver = Mockito.mock(CurrentUserResolver.class);
        when(currentUserResolver.resolve("lmendez")).thenReturn("lmendez");
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T15:05:00Z"), ZoneId.of("America/Bogota"));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RoomController(registerRoomUseCase, consultRoomInventoryUseCase, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorFactory(clock)))
                .build();
    }

    @Test
    @DisplayName("Registro válido responde 201 con la habitación; ignora roomId y status del cliente")
    void register_returns201() throws Exception {
        when(registerRoomUseCase.register(any()))
                .thenReturn(new RoomDto(
                        ROOM_ID, RoomStatus.Available, "407", 4, RoomCategory.DOBLE, 2, new BigDecimal("250000"), 0));

        mockMvc.perform(post("/api/rooms")
                        .header("X-User", "lmendez")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId": "11111111-1111-1111-1111-111111111111", "status": "Inactive",
                                 "roomNumber": "407", "floor": 4, "categoryRoom": "DOBLE",
                                 "maxCapacity": 2, "baseRate": 250000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.status").value("Available"))
                .andExpect(jsonPath("$.roomNumber").value("407"))
                .andExpect(jsonPath("$.floor").value(4))
                .andExpect(jsonPath("$.categoryRoom").value("DOBLE"))
                .andExpect(jsonPath("$.maxCapacity").value(2))
                .andExpect(jsonPath("$.baseRate").value(250000))
                .andExpect(jsonPath("$.version").value(0));

        ArgumentCaptor<RegisterRoomCommand> command = ArgumentCaptor.forClass(RegisterRoomCommand.class);
        verify(registerRoomUseCase).register(command.capture());
        assertThat(command.getValue())
                .isEqualTo(
                        new RegisterRoomCommand("407", 4, RoomCategory.DOBLE, 2, new BigDecimal("250000"), "lmendez"));
    }

    @Test
    @DisplayName("T005: campos faltantes responden 400 VALIDATION_ERROR con la lista de campos pendientes")
    void register_missingFields_returnsAllFields() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .header("X-User", "lmendez")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber": "407", "floor": 4, "baseRate": 250000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Completa los campos obligatorios."))
                .andExpect(jsonPath("$.details.fields", containsInAnyOrder("categoryRoom", "maxCapacity")));

        verify(registerRoomUseCase, never()).register(any());
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "floor        | 0          | El piso debe ser un número entero mayor a 0.",
                "floor        | -3         | El piso debe ser un número entero mayor a 0.",
                "maxCapacity  | 0          | La capacidad máxima debe ser un número entero mayor a 0.",
                "baseRate     | 0          | La tarifa base debe ser mayor a 0 y tener máximo dos decimales.",
                "baseRate     | -1         | La tarifa base debe ser mayor a 0 y tener máximo dos decimales.",
                "baseRate     | 100.123    | La tarifa base debe ser mayor a 0 y tener máximo dos decimales.",
                "roomNumber   | \"12345678901\" | El número de habitación debe tener máximo 10 caracteres.",
                "categoryRoom | \"PENTHOUSE\" | Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique."
            })
    @DisplayName("T006: valores fuera de regla responden 400 VALIDATION_ERROR con su mensaje y campo")
    void register_invalidValue_returns400(String field, String value, String message) throws Exception {
        String body = """
                {"roomNumber": "407", "floor": 4, "categoryRoom": "DOBLE", "maxCapacity": 2, "baseRate": 250000}
                """.replaceFirst("\"" + field + "\": [^,}]+", "\"" + field + "\": " + value);

        mockMvc.perform(post("/api/rooms")
                        .header("X-User", "lmendez")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.details.field").value(field));
    }

    @Test
    @DisplayName("T006: un piso no entero se rechaza con VALIDATION_ERROR en el campo floor")
    void register_nonIntegerFloor_returns400() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .header("X-User", "lmendez")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber": "407", "floor": 4.5, "categoryRoom": "DOBLE", "maxCapacity": 2,
                                 "baseRate": 250000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("floor"));
    }

    @Test
    @DisplayName("Número repetido responde 409 ROOM_NUMBER_ALREADY_EXISTS")
    void register_duplicate_returns409() throws Exception {
        when(registerRoomUseCase.register(any())).thenThrow(new RoomNumberAlreadyExistsException("204"));

        mockMvc.perform(post("/api/rooms")
                        .header("X-User", "lmendez")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber": "204", "floor": 2, "categoryRoom": "DOBLE", "maxCapacity": 2,
                                 "baseRate": 250000}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NUMBER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Ya existe una habitación con el número 204."));
    }

    @Test
    @DisplayName("Sin X-User responde 400 CURRENT_USER_REQUIRED y no registra")
    void register_withoutCurrentUser_returns400() throws Exception {
        when(currentUserResolver.resolve(null)).thenThrow(new CurrentUserRequiredException());

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber": "407", "floor": 4, "categoryRoom": "DOBLE", "maxCapacity": 2,
                                 "baseRate": 250000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));

        verify(registerRoomUseCase, never()).register(any());
    }

    @Test
    @DisplayName("GET /inventory sin parámetros pide la página 1 de 10, sin filtros y por número ascendente")
    void inventory_defaults() throws Exception {
        when(consultRoomInventoryUseCase.search(RoomInventoryFilter.none(), 1, 10))
                .thenReturn(new PagedResultDto<>(List.of(room()), 1, 10, 24, 3));

        mockMvc.perform(get("/api/rooms/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("Available"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(24))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    @DisplayName("GET /inventory traduce filtros, orden y página; normaliza el número (espacios y mayúsculas)")
    void inventory_mapsParameters() throws Exception {
        RoomInventoryFilter expected = new RoomInventoryFilter(
                "203A", RoomCategory.DOBLE, 2, RoomStatus.Inactive, RoomInventorySort.BASE_RATE, true);
        when(consultRoomInventoryUseCase.search(expected, 2, 5))
                .thenReturn(new PagedResultDto<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/rooms/inventory")
                        .param("roomNumber", " 203a ")
                        .param("categoryRoom", "DOBLE")
                        .param("floor", "2")
                        .param("status", "Inactive")
                        .param("sort", "baseRate")
                        .param("direction", "desc")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "size, 101",
        "size, 0",
        "page, 0",
        "floor, -1",
        "floor, dos",
        "sort, roomId",
        "direction, up",
        "categoryRoom, PENTHOUSE",
        "status, Ocupada"
    })
    @DisplayName("GET /inventory con un parámetro fuera de sus valores responde 400 VALIDATION_ERROR con el campo")
    void inventory_invalidParameter_returns400(String field, String value) throws Exception {
        mockMvc.perform(get("/api/rooms/inventory").param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value(field));

        verify(consultRoomInventoryUseCase, never()).search(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /inventory/{roomId} devuelve el detalle con version")
    void inventoryDetail_returnsRoom() throws Exception {
        when(consultRoomInventoryUseCase.getById(ROOM_ID)).thenReturn(room());

        mockMvc.perform(get("/api/rooms/inventory/{roomId}", ROOM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("407"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("GET /inventory/{roomId} responde 404 ROOM_NOT_FOUND si no existe y 400 si no es un UUID")
    void inventoryDetail_errors() throws Exception {
        when(consultRoomInventoryUseCase.getById(ROOM_ID)).thenThrow(new RoomNotFoundException());

        mockMvc.perform(get("/api/rooms/inventory/{roomId}", ROOM_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró la habitación."));
        mockMvc.perform(get("/api/rooms/inventory/{roomId}", "no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName(
            "Módulo 2: GET /api/rooms?categoryRoom= responde el arreglo con solo id, roomNumber, categoryRoom y maxCapacity")
    void sellableByCategory_returnsOnlyModule2Fields() throws Exception {
        when(consultRoomInventoryUseCase.listSellableByCategory(RoomCategory.DOBLE))
                .thenReturn(List.of(new Module2RoomDto(ROOM_ID, "201", RoomCategory.DOBLE, 2)));

        mockMvc.perform(get("/api/rooms").param("categoryRoom", "DOBLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$[0].roomNumber").value("201"))
                .andExpect(jsonPath("$[0].categoryRoom").value("DOBLE"))
                .andExpect(jsonPath("$[0].maxCapacity").value(2))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].baseRate").doesNotExist())
                .andExpect(jsonPath("$[0].version").doesNotExist());
    }

    @Test
    @DisplayName("Módulo 2: una categoría sin habitaciones vendibles responde []")
    void sellableByCategory_empty_returnsEmptyArray() throws Exception {
        when(consultRoomInventoryUseCase.listSellableByCategory(RoomCategory.BOUTIQUE))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/rooms").param("categoryRoom", "BOUTIQUE"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @ParameterizedTest(name = "categoryRoom = {0}")
    @CsvSource(
            nullValues = "null",
            value = {"null", "PENTHOUSE", "doble"})
    @DisplayName("Módulo 2: sin categoryRoom o con una categoría inválida responde 400 VALIDATION_ERROR")
    void sellableByCategory_invalidCategory_returns400(String categoryRoom) throws Exception {
        var request = get("/api/rooms");
        if (categoryRoom != null) {
            request = request.param("categoryRoom", categoryRoom);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("categoryRoom"));
    }

    @Test
    @DisplayName(
            "Módulo 2: GET /api/rooms/{roomId} responde solo los 4 campos; 404 si no es vendible y 400 si no es un UUID")
    void sellableById_contract() throws Exception {
        UUID inactive = UUID.randomUUID();
        when(consultRoomInventoryUseCase.getSellableById(ROOM_ID))
                .thenReturn(new Module2RoomDto(ROOM_ID, "201", RoomCategory.DOBLE, 2));
        when(consultRoomInventoryUseCase.getSellableById(inactive)).thenThrow(new RoomNotFoundException());

        mockMvc.perform(get("/api/rooms/{roomId}", ROOM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("201"))
                .andExpect(jsonPath("$.status").doesNotExist());
        mockMvc.perform(get("/api/rooms/{roomId}", inactive))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));
        mockMvc.perform(get("/api/rooms/{roomId}", "no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    private static RoomDto room() {
        return new RoomDto(ROOM_ID, RoomStatus.Available, "407", 4, RoomCategory.DOBLE, 2, new BigDecimal("250000"), 0);
    }
}

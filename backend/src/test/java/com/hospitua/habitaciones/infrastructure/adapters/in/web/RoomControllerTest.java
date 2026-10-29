package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.domain.exception.CurrentUserRequiredException;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
    private CurrentUserResolver currentUserResolver;

    @BeforeEach
    void setUp() {
        registerRoomUseCase = Mockito.mock(RegisterRoomUseCase.class);
        currentUserResolver = Mockito.mock(CurrentUserResolver.class);
        when(currentUserResolver.resolve("lmendez")).thenReturn("lmendez");
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T15:05:00Z"), ZoneId.of("America/Bogota"));

        mockMvc = MockMvcBuilders.standaloneSetup(new RoomController(registerRoomUseCase, currentUserResolver))
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
}

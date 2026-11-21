package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.application.dto.Module2RoomDto;
import com.hospitua.habitaciones.application.dto.RoomBaseRateResponseDto;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomInventoryUseCase;
import com.hospitua.habitaciones.application.ports.in.DecommissionRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.EditRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.GetRoomBaseRateUseCase;
import com.hospitua.habitaciones.application.ports.in.GetRoomDecommissionUseCase;
import com.hospitua.habitaciones.application.ports.in.ReactivateRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.infrastructure.config.IntegrationProperties;
import com.hospitua.habitaciones.infrastructure.config.IntegrationProperties.Client;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Llaves de integración sobre las rutas reales de los controllers (plan de integración, Fase 1). */
class IntegrationKeyInterceptorTest {

    private static final String KEY_MODULE3 = "llave-modulo3";
    private static final UUID ROOM = UUID.fromString("a0000000-0000-0000-0000-000000000105");
    private static final String BASE_RATE = "/api/rooms/" + ROOM + "/base-rate?date=2026-11-20";

    private final GetRoomBaseRateUseCase baseRateUseCase = Mockito.mock(GetRoomBaseRateUseCase.class);
    private final ConsultRoomInventoryUseCase inventoryUseCase = Mockito.mock(ConsultRoomInventoryUseCase.class);

    @BeforeEach
    void setUp() {
        when(baseRateUseCase.getBaseRate(any(), any()))
                .thenReturn(new RoomBaseRateResponseDto(ROOM, LocalDate.of(2026, 11, 20), "320000.00", "COP"));
        when(inventoryUseCase.getSellableById(any()))
                .thenReturn(new Module2RoomDto(ROOM, "105", RoomCategory.SUITE, 4));
    }

    private MockMvc mockMvc(Map<String, Client> clients) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Bogota"));
        RoomController roomController = new RoomController(
                Mockito.mock(RegisterRoomUseCase.class),
                inventoryUseCase,
                Mockito.mock(DecommissionRoomUseCase.class),
                Mockito.mock(GetRoomDecommissionUseCase.class),
                Mockito.mock(ReactivateRoomUseCase.class),
                Mockito.mock(EditRoomUseCase.class),
                Mockito.mock(CurrentUserResolver.class));
        return MockMvcBuilders.standaloneSetup(new RoomBaseRateController(baseRateUseCase), roomController)
                .addInterceptors(new IntegrationKeyInterceptor(new IntegrationProperties(clients)))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorFactory(clock)))
                .build();
    }

    private MockMvc withModule3Key() {
        return mockMvc(Map.of("module3", new Client(KEY_MODULE3, List.of("GET /api/rooms/{roomId}/base-rate"))));
    }

    @Test
    @DisplayName("Con la llave correcta del cliente, la consulta responde 200")
    void acceptsTheClientKey() throws Exception {
        withModule3Key()
                .perform(get(BASE_RATE).header(IntegrationProperties.KEY_HEADER, KEY_MODULE3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseRate").value("320000.00"));
    }

    @Test
    @DisplayName("Sin la llave responde 401 INTEGRATION_UNAUTHORIZED y no llega al caso de uso")
    void rejectsMissingKey() throws Exception {
        withModule3Key()
                .perform(get(BASE_RATE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INTEGRATION_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("La consulta requiere una llave de integración válida."));
        Mockito.verifyNoInteractions(baseRateUseCase);
    }

    @Test
    @DisplayName("Con una llave incorrecta (o la de otro cliente) responde 401")
    void rejectsWrongKey() throws Exception {
        MockMvc mockMvc = mockMvc(Map.of(
                "module3", new Client(KEY_MODULE3, List.of("GET /api/rooms/{roomId}/base-rate")),
                "module2", new Client("llave-modulo2", List.of("GET /api/rooms/{roomId}"))));
        mockMvc.perform(get(BASE_RATE).header(IntegrationProperties.KEY_HEADER, "llave-modulo2"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE_RATE).header(IntegrationProperties.KEY_HEADER, KEY_MODULE3 + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Una ruta que no es del cliente no pide llave (GET /api/rooms/{roomId} sigue abierta)")
    void otherRoutesStayOpen() throws Exception {
        withModule3Key().perform(get("/api/rooms/" + ROOM)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un cliente sin llave configurada deja sus rutas abiertas")
    void clientWithoutKeyKeepsRoutesOpen() throws Exception {
        mockMvc(Map.of("module3", new Client("", List.of("GET /api/rooms/{roomId}/base-rate"))))
                .perform(get(BASE_RATE))
                .andExpect(status().isOk());
    }
}

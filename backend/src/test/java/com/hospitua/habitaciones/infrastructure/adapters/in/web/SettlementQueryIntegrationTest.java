package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.Stay;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/stays/{stayId}/settlement} sobre la aplicación completa, PostgreSQL real y Módulo 3 en WireMock
 * (consultar-liquidacion T009, T014, T017, T019, T025). WireMock escucha en el puerto de {@code MODULE3_BASE_URL} del
 * perfil {@code test}, así la prueba usa el contexto compartido en lugar de levantar otro con sus propios contenedores.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementQueryIntegrationTest {

    /** Puerto por defecto de {@code hospitua.module3.base-url} en el perfil {@code test}. */
    private static final int MODULE3_PORT = 8083;

    private static final String OTA_SETTLEMENT = """
            {"settlementType":"INFORMATIVE","channel":"OTA","otaId":"BOOKING","invoice":null,
             "breakdown":{"lodgingAmount":"500000.00","otaCommissionPercentage":"15.00",
                          "otaCommissionAmount":"75000.00","netIncome":"425000.00"}}
            """;
    private static final String DIRECT_SETTLEMENT = """
            {"settlementType":"INFORMATIVE","channel":"DIRECT","otaId":null,"invoice":null,
             "breakdown":{"lodgingAmount":"380000.50","otaCommissionPercentage":null,
                          "otaCommissionAmount":"0.00","netIncome":"380000.50"}}
            """;

    private static WireMockServer module3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private StayRepositoryPort stayRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private String ref;
    private Room room;

    @BeforeAll
    static void startModule3() {
        module3 = new WireMockServer(WireMockConfiguration.wireMockConfig().port(MODULE3_PORT));
        module3.start();
    }

    @AfterAll
    static void stopModule3() {
        module3.stop();
    }

    @BeforeEach
    void setUp() {
        module3.resetAll();
        // Las caídas simuladas cuentan para el circuito, que es un bean compartido con las demás pruebas
        circuitBreakers.circuitBreaker("module3").reset();
        today = LocalDate.now(clock);
        ref = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        room = roomRepository.save(new Room(
                UUID.randomUUID(),
                "L" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                3,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("250000"),
                RoomStatus.Occupied,
                null,
                0));
    }

    @Test
    @DisplayName("T009: reserva OTA con la comisión, la fuente de la estancia, las noches y Cache-Control no-store")
    void otaSettlement() throws Exception {
        UUID stayId = givenStay(null, "BOOKING");
        stubSettlement(200, OTA_SETTLEMENT);

        settlement(stayId)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.settlementType").value("INFORMATIVE"))
                .andExpect(jsonPath("$.reservationInfo.source").value("BOOKING"))
                .andExpect(jsonPath("$.reservationInfo.otaCommissionPercentage").value(15.00))
                .andExpect(jsonPath("$.reservationInfo.otaCommissionAmount").value(75000.00))
                .andExpect(jsonPath("$.reservationInfo.netIncome").value(425000.00))
                .andExpect(jsonPath("$.guestSummary.lodgingAmount").value(500000.00))
                .andExpect(jsonPath("$.stayData.nights").value(2))
                .andExpect(jsonPath("$.stayData.checkInDate")
                        .value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.stayData.checkOutDate").value(today.toString()))
                .andExpect(jsonPath("$.unavailableCause").doesNotExist());
        module3.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/settlements"))
                .withQueryParam("reservationRef", WireMock.equalTo(ref))
                .withQueryParam("roomId", WireMock.equalTo(room.getId().toString())));
    }

    @Test
    @DisplayName("T009: reserva directa sin porcentaje de comisión, comisión 0 e ingreso neto igual al hospedaje")
    void directSettlement() throws Exception {
        UUID stayId = givenStay(null, "DIRECT");
        stubSettlement(200, DIRECT_SETTLEMENT);

        settlement(stayId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationInfo.source").value("DIRECT"))
                .andExpect(jsonPath("$.reservationInfo.otaCommissionPercentage").doesNotExist())
                .andExpect(jsonPath("$.reservationInfo.otaCommissionAmount").value(0.00))
                .andExpect(jsonPath("$.reservationInfo.netIncome").value(380000.50))
                .andExpect(jsonPath("$.guestSummary.lodgingAmount").value(380000.50));
    }

    @Test
    @DisplayName("T019/T025: sin respuesta de Módulo 3 en 3 s responde 200 UNAVAILABLE con TIMEOUT y sin montos")
    void slowModule3_isUnavailableTimeout() throws Exception {
        UUID stayId = givenStay(null, "BOOKING");
        module3.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/settlements"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(OTA_SETTLEMENT)
                        .withFixedDelay(3_500)));

        settlement(stayId)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableCause").value("TIMEOUT"))
                .andExpect(jsonPath("$.reservationInfo").doesNotExist())
                .andExpect(jsonPath("$.guestSummary").doesNotExist())
                .andExpect(jsonPath("$.stayData").doesNotExist());
    }

    @Test
    @DisplayName("Un 4xx de Módulo 3 con ApiError responde 200 UNAVAILABLE con su errorCode")
    void businessError_isUnavailableWithCause() throws Exception {
        UUID stayId = givenStay(null, "BOOKING");
        stubSettlement(409, "{\"errorCode\":\"MISSING_COMMISSION\",\"message\":\"x\"}");

        settlement(stayId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableCause").value("MISSING_COMMISSION"));
    }

    @Test
    @DisplayName("Una estancia cerrada o inexistente responde 404 ACTIVE_STAY_NOT_FOUND, y un stayId inválido 400")
    void closedMissingOrInvalidStay() throws Exception {
        UUID closedStayId = givenStay(today, "BOOKING");

        settlement(closedStayId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_STAY_NOT_FOUND"));
        settlement(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_STAY_NOT_FOUND"));
        mockMvc.perform(get("/api/stays/no-es-un-uuid/settlement"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        module3.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }

    private UUID givenStay(LocalDate checkOutDate, String source) {
        return stayRepository
                .save(new Stay(
                        UUID.randomUUID(),
                        ref,
                        room.getId(),
                        source,
                        today.minusDays(2),
                        checkOutDate,
                        today.minusDays(2),
                        today,
                        "Ana",
                        "Pérez",
                        "123",
                        0))
                .getId();
    }

    private ResultActions settlement(UUID stayId) throws Exception {
        return mockMvc.perform(get("/api/stays/" + stayId + "/settlement"));
    }

    private static void stubSettlement(int status, String body) {
        module3.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/settlements"))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}

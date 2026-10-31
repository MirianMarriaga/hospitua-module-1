package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException;
import com.hospitua.habitaciones.domain.model.SettlementRequest;
import com.hospitua.habitaciones.domain.model.SettlementSummary;
import com.hospitua.habitaciones.domain.model.SettlementType;
import com.hospitua.habitaciones.infrastructure.config.RestClientConfig;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Cliente de {@code GET /api/settlements} de Módulo 3 contra WireMock (consultar-liquidacion T007, T019, T020, T022).
 * El adaptador se crea sin Spring, así que aquí no actúa el circuito: lo cubre
 * {@code SettlementCircuitBreakerIntegrationTest}. Como en el cliente de Módulo 2, WireMock se calienta antes de las
 * pruebas.
 */
@ExtendWith(OutputCaptureExtension.class)
class SettlementRestAdapterTest {

    private static final UUID ROOM_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final SettlementRequest REQUEST = new SettlementRequest("RES-000123", ROOM_ID);

    /** Respuesta 200 del Endpoint 1 del plan (reserva OTA). */
    private static final String OTA_EXAMPLE = """
            {
              "settlementType": "INFORMATIVE",
              "reservationRef": "RES-000123",
              "roomId": "123e4567-e89b-12d3-a456-426614174000",
              "categoryRoom": null,
              "channel": "OTA",
              "otaId": "BOOKING",
              "currency": "COP",
              "breakdown": {
                "baseLodgingAmount": "500000.00",
                "dynamicAdjustmentAmount": "0.00",
                "lodgingAmount": "500000.00",
                "otaCommissionPercentage": "15.00",
                "otaCommissionAmount": "75000.00",
                "netIncome": "425000.00"
              },
              "invoice": null,
              "generatedAt": "2026-10-06T11:30:00-05:00"
            }
            """;

    private static final String DIRECT_EXAMPLE = """
            {
              "settlementType": "INFORMATIVE",
              "channel": "DIRECT",
              "otaId": null,
              "breakdown": {
                "lodgingAmount": "380000.50",
                "otaCommissionPercentage": null,
                "otaCommissionAmount": "0.00",
                "netIncome": "380000.50"
              },
              "invoice": null
            }
            """;

    private static WireMockServer module3;
    private static SettlementRestAdapter adapter;

    @BeforeAll
    static void startModule3() {
        module3 = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        module3.start();
        adapter = adapterFor("http://localhost:" + module3.port(), Duration.ofSeconds(3));
        warmUp();
    }

    /** Una petición descartada: la primera contra WireMock en frío carga el cliente y el servidor. */
    private static void warmUp() {
        stubSettlement(200, OTA_EXAMPLE);
        try {
            adapter.querySettlement(REQUEST);
        } catch (ExternalModule3UnavailableException slowFirstRequest) {
            // Solo importa haber cargado las clases; las pruebas empiezan con los stubs limpios
        }
        module3.resetAll();
    }

    @AfterAll
    static void stopModule3() {
        module3.stop();
    }

    @BeforeEach
    void resetStubs() {
        module3.resetAll();
    }

    @Test
    @DisplayName("T007: envía solo reservationRef y roomId, y mapea el desglose OTA sin alterar los montos")
    void sendsContractAndMapsOtaSettlement() {
        stubSettlement(200, OTA_EXAMPLE);

        SettlementSummary summary = adapter.querySettlement(REQUEST);

        module3.verify(getRequestedFor(urlPathEqualTo("/api/settlements"))
                .withQueryParam("reservationRef", equalTo("RES-000123"))
                .withQueryParam("roomId", equalTo(ROOM_ID.toString()))
                .withHeader("Accept", equalTo("application/json")));
        List<LoggedRequest> requests = module3.findAll(getRequestedFor(urlPathEqualTo("/api/settlements")));
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().getQueryParams()).containsOnlyKeys("reservationRef", "roomId");
        assertThat(requests.getFirst().containsHeader("X-User")).isFalse();
        assertThat(summary)
                .isEqualTo(new SettlementSummary(
                        SettlementType.INFORMATIVE,
                        new BigDecimal("500000.00"),
                        new BigDecimal("15.00"),
                        new BigDecimal("75000.00"),
                        new BigDecimal("425000.00")));
    }

    @Test
    @DisplayName(
            "T007: en una reserva directa la comisión es nula en porcentaje y cero en valor, con la escala recibida")
    void mapsDirectSettlement() {
        stubSettlement(200, DIRECT_EXAMPLE);

        SettlementSummary summary = adapter.querySettlement(REQUEST);

        assertThat(summary.otaCommissionPercentage()).isNull();
        assertThat(summary.otaCommissionAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.lodgingAmount()).isEqualTo(new BigDecimal("380000.50"));
        assertThat(summary.netIncome()).isEqualTo(summary.lodgingAmount());
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "400, INVALID_QUERY_PARAMS",
        "404, RESERVATION_NOT_FOUND",
        "404, QUOTE_NOT_FOUND",
        "409, MISSING_COMMISSION",
        "409, INVALID_COMMISSION",
        "422, UNEXPECTED_ERROR",
        "424, MODULE2_UNAVAILABLE",
        "503, SERVICE_DOWN"
    })
    @DisplayName("T020/T022: una respuesta no 2xx con ApiError da como causa su errorCode")
    void apiError_causeIsErrorCode(int status, String errorCode) {
        stubSettlement(
                status,
                "{\"errorCode\":\"" + errorCode + "\",\"message\":\"x\",\"timestamp\":\"2026-10-06T11:30:00-05:00\","
                        + "\"path\":\"/api/settlements\"}");

        assertThat(causeOf(adapter)).isEqualTo(errorCode);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {"", "BAD\\nCODE", "no-es-un-codigo", "CODIGO_DEMASIADO_LARGO_PARA_SER_UNA_CAUSA_DE_MODULO_3_X"})
    @DisplayName("T020/T022: un errorCode vacío o que no tiene la forma de un código da HTTP_ERROR")
    void malformedErrorCode_isHttpError(String errorCode) {
        stubSettlement(404, "{\"errorCode\":\"" + errorCode + "\",\"message\":\"x\"}");

        assertThat(causeOf(adapter)).isEqualTo("HTTP_ERROR");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {500, 502, 404})
    @DisplayName("T020/T022: una respuesta no 2xx sin ApiError da HTTP_ERROR")
    void nonApiError_isHttpError(int status) {
        module3.stubFor(get(urlPathEqualTo("/api/settlements"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html>Bad Gateway</html>")));

        assertThat(causeOf(adapter)).isEqualTo("HTTP_ERROR");
    }

    @Test
    @DisplayName("T019/T022: sin respuesta dentro del read-timeout la causa es TIMEOUT")
    void slowResponse_isTimeout() {
        SettlementRestAdapter impatient = adapterFor("http://localhost:" + module3.port(), Duration.ofMillis(500));
        module3.stubFor(get(urlPathEqualTo("/api/settlements"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(OTA_EXAMPLE)
                        .withFixedDelay(2_000)));

        assertThat(causeOf(impatient)).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("T022: si no se puede conectar con Módulo 3 la causa es CONNECTION_ERROR")
    void unreachable_isConnectionError() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThat(causeOf(adapterFor("http://localhost:" + closedPort, Duration.ofSeconds(3))))
                .isEqualTo("CONNECTION_ERROR");
    }

    @ParameterizedTest(name = "{index}")
    @ValueSource(
            strings = {
                "no es json",
                "{\"settlementType\":\"INFORMATIVE\"}",
                "{\"settlementType\":\"PROVISIONAL\",\"breakdown\":{\"lodgingAmount\":\"1.00\","
                        + "\"otaCommissionAmount\":\"0.00\",\"netIncome\":\"1.00\"}}",
                "{\"settlementType\":\"INFORMATIVE\",\"breakdown\":{\"lodgingAmount\":\"abc\","
                        + "\"otaCommissionAmount\":\"0.00\",\"netIncome\":\"1.00\"}}",
                "{\"settlementType\":\"INFORMATIVE\",\"breakdown\":{\"lodgingAmount\":\"1.00\","
                        + "\"otaCommissionAmount\":\"0.00\"}}"
            })
    @DisplayName("T022: una liquidación ilegible, sin desglose o con un monto ausente o no decimal da INVALID_RESPONSE")
    void unreadableSettlement_isInvalidResponse(String body) {
        stubSettlement(200, body);

        assertThat(causeOf(adapter)).isEqualTo("INVALID_RESPONSE");
    }

    @Test
    @DisplayName("El log de una falla lleva la reserva, la habitación y la causa, sin montos")
    void failureLog_hasNoAmounts(CapturedOutput output) {
        stubSettlement(
                200,
                "{\"settlementType\":\"INFORMATIVE\",\"breakdown\":{\"lodgingAmount\":\"987654.32\","
                        + "\"otaCommissionAmount\":\"0.00\"}}");

        causeOf(adapter);

        assertThat(output)
                .contains("RES-000123")
                .contains(ROOM_ID.toString())
                .contains("INVALID_RESPONSE")
                .doesNotContain("987654.32");
    }

    private static String causeOf(SettlementRestAdapter client) {
        ExternalModule3UnavailableException failure =
                catchThrowableOfType(ExternalModule3UnavailableException.class, () -> client.querySettlement(REQUEST));
        assertThat(failure)
                .as("Se esperaba ExternalModule3UnavailableException")
                .isNotNull();
        return failure.unavailableCause();
    }

    private static void stubSettlement(int status, String body) {
        module3.stubFor(get(urlPathEqualTo("/api/settlements"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static SettlementRestAdapter adapterFor(String baseUrl, Duration readTimeout) {
        return new SettlementRestAdapter(new RestClientConfig()
                .module3RestClient(new Module3Properties(baseUrl, Duration.ofSeconds(1), readTimeout)));
    }
}

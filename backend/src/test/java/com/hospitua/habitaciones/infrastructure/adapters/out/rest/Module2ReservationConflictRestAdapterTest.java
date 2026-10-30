package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hospitua.habitaciones.domain.exception.ExternalModule2UnavailableException;
import com.hospitua.habitaciones.domain.model.ReservationConflictQuery;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import com.hospitua.habitaciones.infrastructure.config.RestClientConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Cliente de {@code GET /api/reservations} de Módulo 2 contra WireMock (consultar-reservas T023, T024). El read-timeout
 * es de 2 s, con margen para la primera petición a WireMock en frío, que en equipos lentos supera los 500 ms; los
 * timeouts reales los cubre {@code RestClientTimeoutWireMockTest}.
 */
@ExtendWith(OutputCaptureExtension.class)
class Module2ReservationConflictRestAdapterTest {

    private static final UUID ROOM_ID = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final ReservationConflictQuery QUERY =
            new ReservationConflictQuery(ROOM_ID, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));

    /** Ejemplo del punto 4 de la Phase 5 del plan: cada reserva trae todas sus habitaciones. */
    private static final String PLAN_EXAMPLE = """
            {
              "items": [
                {
                  "reservationRef": "RSV-3F9A1C7B",
                  "status": "ACTIVE",
                  "startDate": "2026-10-21",
                  "endDate": "2026-10-24",
                  "rooms": [
                    { "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", "roomNumber": "106", "categoryRoom": "DOBLE" }
                  ]
                }
              ]
            }
            """;

    private static WireMockServer module2;
    private static Module2ReservationConflictRestAdapter adapter;

    @BeforeAll
    static void startModule2() {
        module2 = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        module2.start();
        adapter = adapterFor("http://localhost:" + module2.port());
    }

    @AfterAll
    static void stopModule2() {
        module2.stop();
    }

    @BeforeEach
    void resetStubs() {
        module2.resetAll();
    }

    @Test
    @DisplayName("T023/T024: envía dateFrom, dateTo, roomId y X-Client: MODULE1, y lee items[] del ejemplo del plan")
    void sendsContractAndReadsReservations() {
        stubReservations(200, PLAN_EXAMPLE);

        ReservationConflictResult result = adapter.checkConflicts(QUERY);

        module2.verify(getRequestedFor(urlPathEqualTo("/api/reservations"))
                .withQueryParam("dateFrom", equalTo("2026-10-20"))
                .withQueryParam("dateTo", equalTo("2026-10-22"))
                .withQueryParam("roomId", equalTo(ROOM_ID.toString()))
                .withHeader("X-Client", equalTo("MODULE1"))
                .withHeader("Accept", equalTo("application/json")));
        assertThat(result.hasConflict()).isTrue();
        assertThat(result.reservations())
                .containsExactly(new ConflictingReservation(
                        "RSV-3F9A1C7B", "ACTIVE", LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 24)));
    }

    @Test
    @DisplayName("T023: items vacío significa que la operación es segura")
    void emptyItems_noConflict() {
        stubReservations(200, "{\"items\":[]}");

        assertThat(adapter.checkConflicts(QUERY).hasConflict()).isFalse();
    }

    @ParameterizedTest(name = "400 {0}")
    @ValueSource(strings = {"INVALID_CLIENT", "DATE_RANGE_TOO_LARGE", "INVALID_ROOM_ID"})
    @DisplayName("T023: un 400 de Módulo 2 da MODULE_2_UNAVAILABLE y deja la alerta de configuración con su errorCode")
    void badRequest_isUnavailableAndAlertsErrorCode(String errorCode, CapturedOutput output) {
        stubReservations(
                400,
                "{\"errorCode\":\"" + errorCode + "\",\"message\":\"x\",\"timestamp\":\"2026-10-20T10:00:00-05:00\","
                        + "\"path\":\"/api/reservations\"}");

        assertThatThrownBy(() -> adapter.checkConflicts(QUERY))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(HttpClientErrorException.class);
        assertThat(output).contains("[MODULE_2_REQUEST_REJECTED]").contains("errorCode=" + errorCode);
    }

    @Test
    @DisplayName("T023: un 5xx da MODULE_2_UNAVAILABLE sin alerta de configuración")
    void serverError_isUnavailable(CapturedOutput output) {
        stubReservations(503, "{}");

        assertThatThrownBy(() -> adapter.checkConflicts(QUERY))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(HttpServerErrorException.class);
        assertThat(output).doesNotContain("[MODULE_2_REQUEST_REJECTED]");
    }

    @Test
    @DisplayName("T023: superar el read-timeout da MODULE_2_UNAVAILABLE")
    void timeout_isUnavailable() {
        module2.stubFor(get(urlPathEqualTo("/api/reservations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[]}")
                        .withFixedDelay(3_000)));

        assertThatThrownBy(() -> adapter.checkConflicts(QUERY))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("T023: Módulo 2 caído (conexión rechazada) da MODULE_2_UNAVAILABLE")
    void connectionRefused_isUnavailable() {
        Module2ReservationConflictRestAdapter downAdapter = adapterFor("http://localhost:1");

        assertThatThrownBy(() -> downAdapter.checkConflicts(QUERY))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("Una respuesta ilegible o sin items da MODULE_2_UNAVAILABLE, nunca una operación sin verificar")
    void unreadableOrMissingItems_isUnavailable() {
        stubReservations(200, "no es json");
        assertThatThrownBy(() -> adapter.checkConflicts(QUERY))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(RestClientException.class);

        stubReservations(200, "{}");
        assertThatThrownBy(() -> adapter.checkConflicts(QUERY))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{\"items\":[null]}",
                "{\"items\":[{\"status\":\"ACTIVE\",\"startDate\":\"2026-10-21\",\"endDate\":\"2026-10-24\"}]}",
                "{\"items\":[{\"reservationRef\":\"RSV-1\",\"startDate\":\"2026-10-21\",\"endDate\":\"2026-10-24\"}]}",
                "{\"items\":[{\"reservationRef\":\"RSV-1\",\"status\":\"ACTIVE\",\"endDate\":\"2026-10-24\"}]}",
                "{\"items\":[{\"reservationRef\":\"RSV-1\",\"status\":\"ACTIVE\",\"startDate\":\"2026-10-21\"}]}"
            })
    @DisplayName("Una reserva sin referencia, estado o fechas da MODULE_2_UNAVAILABLE, nunca un conflicto a medias")
    void incompleteReservation_isUnavailable(String body, CapturedOutput output) {
        stubReservations(200, body);

        assertThatThrownBy(() -> adapter.checkConflicts(QUERY))
                .isInstanceOf(ExternalModule2UnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(output).contains("reserva incompleta");
    }

    private static void stubReservations(int status, String body) {
        module2.stubFor(get(urlPathEqualTo("/api/reservations"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static Module2ReservationConflictRestAdapter adapterFor(String baseUrl) {
        Module2Properties properties = new Module2Properties(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2));
        return new Module2ReservationConflictRestAdapter(new RestClientConfig().module2RestClient(properties));
    }
}

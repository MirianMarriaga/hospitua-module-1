package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hospitua.habitaciones.application.ports.out.SettlementRestQueryPort;
import com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException;
import com.hospitua.habitaciones.domain.model.SettlementRequest;
import com.hospitua.habitaciones.domain.model.SettlementType;
import com.hospitua.habitaciones.infrastructure.config.RestClientConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Circuito {@code module3} del adaptador con la configuración real de {@code application.yml} (consultar-liquidacion
 * T023). El contexto solo tiene el adaptador, AOP y Resilience4j, apuntando a WireMock: no necesita base de datos ni
 * RabbitMQ.
 */
@SpringBootTest(classes = SettlementCircuitBreakerIntegrationTest.CircuitContext.class)
@ActiveProfiles("test")
class SettlementCircuitBreakerIntegrationTest {

    private static final SettlementRequest REQUEST = new SettlementRequest("RES-000123", UUID.randomUUID());
    private static final WireMockServer MODULE3 = startModule3();

    @Autowired
    private SettlementRestQueryPort settlementPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private static WireMockServer startModule3() {
        WireMockServer server =
                new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @AfterAll
    static void stopModule3() {
        MODULE3.stop();
    }

    @BeforeEach
    void reset() {
        MODULE3.resetAll();
        circuitBreaker().reset();
    }

    @Test
    @DisplayName("T023: tras 5 caídas seguidas el circuito se abre y la siguiente consulta no llama a Módulo 3")
    void outages_openCircuit() {
        stub(503, "{}");

        for (int i = 0; i < 5; i++) {
            assertThat(causeOfFailure()).isEqualTo("HTTP_ERROR");
        }

        assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(causeOfFailure()).isEqualTo("CIRCUIT_OPEN");
        MODULE3.verify(5, getRequestedFor(urlPathEqualTo("/api/settlements")));
    }

    @Test
    @DisplayName("T023: las respuestas 4xx de negocio no abren el circuito y no hay reintentos")
    void businessErrors_keepCircuitClosed() {
        stub(404, "{\"errorCode\":\"RESERVATION_NOT_FOUND\"}");

        for (int i = 0; i < 6; i++) {
            assertThat(causeOfFailure()).isEqualTo("RESERVATION_NOT_FOUND");
        }

        assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        MODULE3.verify(6, getRequestedFor(urlPathEqualTo("/api/settlements")));
    }

    @Test
    @DisplayName("Con el circuito cerrado la liquidación llega por el adaptador")
    void closedCircuit_returnsSettlement() {
        stub(
                200,
                "{\"settlementType\":\"INFORMATIVE\",\"breakdown\":{\"lodgingAmount\":\"500000.00\","
                        + "\"otaCommissionPercentage\":\"15.00\",\"otaCommissionAmount\":\"75000.00\","
                        + "\"netIncome\":\"425000.00\"}}");

        assertThat(settlementPort.querySettlement(REQUEST).settlementType()).isEqualTo(SettlementType.INFORMATIVE);
    }

    private String causeOfFailure() {
        ExternalModule3UnavailableException failure = catchThrowableOfType(
                ExternalModule3UnavailableException.class, () -> settlementPort.querySettlement(REQUEST));
        assertThat(failure)
                .as("Se esperaba ExternalModule3UnavailableException")
                .isNotNull();
        return failure.unavailableCause();
    }

    private CircuitBreaker circuitBreaker() {
        return circuitBreakers.circuitBreaker(SettlementRestAdapter.CIRCUIT_BREAKER);
    }

    private static void stub(int status, String body) {
        MODULE3.stubFor(get(urlPathEqualTo("/api/settlements"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
    @Import(SettlementRestAdapter.class)
    static class CircuitContext {

        @Bean
        RestClient module3RestClient() {
            return new RestClientConfig()
                    .module3RestClient(new Module3Properties(
                            "http://localhost:" + MODULE3.port(), Duration.ofSeconds(1), Duration.ofSeconds(3)));
        }
    }
}

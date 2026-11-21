package com.hospitua.habitaciones.infrastructure.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hospitua.habitaciones.infrastructure.adapters.out.rest.Module2Properties;
import com.hospitua.habitaciones.infrastructure.adapters.out.rest.Module3Properties;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestClientTimeoutWireMockTest {

    private static WireMockServer wireMockServer;
    private static RestClient module2Client;
    private static RestClient module3Client;

    @BeforeAll
    static void setUp() {
        wireMockServer =
                new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        String wireMockUrl = "http://localhost:" + wireMockServer.port();
        RestClientConfig config = new RestClientConfig();

        Module2Properties m2Props =
                new Module2Properties(wireMockUrl, Duration.ofSeconds(1), Duration.ofSeconds(2), "");
        module2Client = config.module2RestClient(m2Props);

        Module3Properties m3Props =
                new Module3Properties(wireMockUrl, Duration.ofSeconds(1), Duration.ofSeconds(3), "");
        module3Client = config.module3RestClient(m3Props);
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Cliente Módulo 2 responde exitosamente cuando la respuesta es rápida")
    void module2ClientShouldSucceedOnFastResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/api/reservations/fast"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        String response =
                module2Client.get().uri("/api/reservations/fast").retrieve().body(String.class);

        assertThat(response).contains("ok");
    }

    @Test
    @DisplayName("Cliente Módulo 2 corta al pasar el read-timeout de 2 s (retraso de 3 s)")
    void module2ClientShouldTimeoutAfterTwoSeconds() {
        wireMockServer.stubFor(get(urlEqualTo("/api/reservations/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000).withBody("{\"status\":\"slow\"}")));

        assertThatThrownBy(() -> module2Client
                        .get()
                        .uri("/api/reservations/slow")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("Cliente Módulo 3 responde exitosamente cuando la respuesta es rápida")
    void module3ClientShouldSucceedOnFastResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/api/settlements/fast"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        String response =
                module3Client.get().uri("/api/settlements/fast").retrieve().body(String.class);

        assertThat(response).contains("ok");
    }

    @Test
    @DisplayName("Cliente Módulo 3 corta al pasar el read-timeout de 3 s (retraso de 4 s)")
    void module3ClientShouldTimeoutAfterThreeSeconds() {
        wireMockServer.stubFor(get(urlEqualTo("/api/settlements/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(4000).withBody("{\"status\":\"slow\"}")));

        assertThatThrownBy(() -> module3Client
                        .get()
                        .uri("/api/settlements/slow")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName(
            "Con llave de integración configurada, el cliente la envía en X-Internal-Key; sin ella, no envía la cabecera")
    void internalKeyHeaderOnlyWhenConfigured() {
        String url = "http://localhost:" + wireMockServer.port();
        wireMockServer.stubFor(
                get(urlEqualTo("/api/settlements/llave")).willReturn(aResponse().withStatus(200)));
        RestClient withKey = new RestClientConfig()
                .module3RestClient(
                        new Module3Properties(url, Duration.ofSeconds(1), Duration.ofSeconds(3), "llave-m3"));

        withKey.get().uri("/api/settlements/llave").retrieve().toBodilessEntity();
        module3Client.get().uri("/api/settlements/llave").retrieve().toBodilessEntity();

        wireMockServer.verify(
                1,
                getRequestedFor(urlEqualTo("/api/settlements/llave"))
                        .withHeader(IntegrationProperties.KEY_HEADER, equalTo("llave-m3")));
        wireMockServer.verify(
                1,
                getRequestedFor(urlEqualTo("/api/settlements/llave"))
                        .withHeader(IntegrationProperties.KEY_HEADER, absent()));
    }
}

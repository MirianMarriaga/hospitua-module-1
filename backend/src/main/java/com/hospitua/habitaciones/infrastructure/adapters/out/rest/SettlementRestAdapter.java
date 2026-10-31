package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import static com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException.CIRCUIT_OPEN;
import static com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException.CONNECTION_ERROR;
import static com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException.HTTP_ERROR;
import static com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException.INVALID_RESPONSE;
import static com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException.TIMEOUT;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hospitua.habitaciones.application.ports.out.SettlementRestQueryPort;
import com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException;
import com.hospitua.habitaciones.domain.model.SettlementRequest;
import com.hospitua.habitaciones.domain.model.SettlementSummary;
import com.hospitua.habitaciones.domain.model.SettlementType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente de {@code GET /api/settlements?reservationRef&roomId} de Módulo 3 (consultar-liquidacion T010, T022, T023),
 * con los timeouts de {@code module3RestClient} y el circuito {@value #CIRCUIT_BREAKER}, sin reintentos. Toda falla
 * termina en {@link ExternalModule3UnavailableException} con su causa: el {@code errorCode} del {@code ApiError} de
 * Módulo 3, {@code HTTP_ERROR} si la respuesta no 2xx no lo trae, {@code TIMEOUT}, {@code CONNECTION_ERROR},
 * {@code INVALID_RESPONSE} si la liquidación no se puede leer y {@code CIRCUIT_OPEN} si el circuito no deja llamar. Los
 * montos llegan como texto decimal y solo se convierten a {@link BigDecimal}; el log no los muestra.
 */
@Slf4j
@Component
public class SettlementRestAdapter implements SettlementRestQueryPort {

    static final String SETTLEMENTS_PATH = "/api/settlements";
    static final String CIRCUIT_BREAKER = "module3";

    /** Forma de los códigos del contrato; otro texto no se copia a la respuesta ni al log. */
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z0-9_]{1,50}");

    private final RestClient restClient;

    public SettlementRestAdapter(@Qualifier("module3RestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "circuitOpen")
    public SettlementSummary querySettlement(SettlementRequest request) {
        return toSummary(request, requestSettlement(request));
    }

    /** Con el circuito abierto no se llama a Módulo 3; las demás fallas no pasan por aquí. */
    SettlementSummary circuitOpen(SettlementRequest request, CallNotPermittedException ex) {
        log.warn(
                "Circuito de Módulo 3 abierto: no se consultó la liquidación de la reserva {} (habitación {})",
                request.reservationRef(),
                request.roomId());
        throw new ExternalModule3UnavailableException(CIRCUIT_OPEN, ex);
    }

    /** Llama a Módulo 3 y traduce cualquier error HTTP, de tiempo o de red a su causa. */
    private SettlementResponse requestSettlement(SettlementRequest request) {
        try {
            return restClient
                    .get()
                    .uri(uri -> uri.path(SETTLEMENTS_PATH)
                            .queryParam("reservationRef", request.reservationRef())
                            .queryParam("roomId", request.roomId())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SettlementResponse.class);
        } catch (RestClientResponseException ex) {
            throw unavailable(
                    request, errorCode(ex), "HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(request, isTimeout(ex) ? TIMEOUT : CONNECTION_ERROR, ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw unavailable(request, INVALID_RESPONSE, ex.getMessage(), ex);
        }
    }

    /** Sin tipo, sin desglose o con un monto obligatorio ausente o que no es decimal, la liquidación es ilegible. */
    private static SettlementSummary toSummary(SettlementRequest request, SettlementResponse response) {
        try {
            Breakdown breakdown = response.breakdown();
            return new SettlementSummary(
                    SettlementType.valueOf(response.settlementType()),
                    new BigDecimal(breakdown.lodgingAmount()),
                    breakdown.otaCommissionPercentage() == null
                            ? null
                            : new BigDecimal(breakdown.otaCommissionPercentage()),
                    new BigDecimal(breakdown.otaCommissionAmount()),
                    new BigDecimal(breakdown.netIncome()));
        } catch (RuntimeException ex) {
            throw unavailable(request, INVALID_RESPONSE, "liquidación incompleta o ilegible", ex);
        }
    }

    private static ExternalModule3UnavailableException unavailable(
            SettlementRequest request, String cause, String detail, Throwable ex) {
        log.warn(
                "Módulo 3 no entregó la liquidación de la reserva {} (habitación {}): causa {} ({})",
                request.reservationRef(),
                request.roomId(),
                cause,
                detail);
        return new ExternalModule3UnavailableException(cause, ex);
    }

    /**
     * {@code errorCode} del {@code ApiError} de Módulo 3, o {@code HTTP_ERROR} si la respuesta no lo trae o no tiene la
     * forma de un código.
     */
    private static String errorCode(RestClientResponseException ex) {
        try {
            Module3Error error = ex.getResponseBodyAs(Module3Error.class);
            String code = error == null || error.errorCode() == null
                    ? ""
                    : error.errorCode().strip();
            return ERROR_CODE.matcher(code).matches() ? code : HTTP_ERROR;
        } catch (RuntimeException unreadable) {
            return HTTP_ERROR;
        }
    }

    /** Sin respuesta dentro del read-timeout; no poder conectar a tiempo cuenta como error de red. */
    private static boolean isTimeout(ResourceAccessException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Solo se leen el tipo y el desglose; {@code categoryRoom}, {@code channel}, {@code invoice} y el resto no. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SettlementResponse(String settlementType, Breakdown breakdown) {}

    /** Montos como texto decimal; {@code otaCommissionPercentage} es nulo en una reserva directa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Breakdown(
            String lodgingAmount, String otaCommissionPercentage, String otaCommissionAmount, String netIncome) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Module3Error(String errorCode) {}
}

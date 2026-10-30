package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hospitua.habitaciones.application.ports.out.Module2ReservationConflictClientPort;
import com.hospitua.habitaciones.domain.exception.ExternalModule2UnavailableException;
import com.hospitua.habitaciones.domain.model.ReservationConflictQuery;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente de {@code GET /api/reservations?dateFrom&dateTo&roomId} de Módulo 2 (consultar-reservas T003, T026), con los
 * timeouts de {@code module2RestClient}. Toda falla (4xx, 5xx, timeout, caída o respuesta ilegible) termina en
 * {@link ExternalModule2UnavailableException}; un 4xx además deja una alerta de configuración en el log de errores con
 * el {@code errorCode} de Módulo 2, porque indica un contrato roto (p. ej. {@code INVALID_CLIENT}) y no una caída.
 */
@Slf4j
@Component
public class Module2ReservationConflictRestAdapter implements Module2ReservationConflictClientPort {

    static final String RESERVATIONS_PATH = "/api/reservations";
    static final String CLIENT_HEADER = "X-Client";
    static final String CLIENT_ID = "MODULE1";

    private final RestClient restClient;

    public Module2ReservationConflictRestAdapter(@Qualifier("module2RestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ReservationConflictResult checkConflicts(ReservationConflictQuery query) {
        ReservationsResponse response;
        try {
            response = restClient
                    .get()
                    .uri(uri -> uri.path(RESERVATIONS_PATH)
                            .queryParam("dateFrom", query.startDate())
                            .queryParam("dateTo", query.endDate())
                            .queryParam("roomId", query.roomId())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header(CLIENT_HEADER, CLIENT_ID)
                    .retrieve()
                    .body(ReservationsResponse.class);
        } catch (HttpClientErrorException ex) {
            log.error(
                    "[MODULE_2_REQUEST_REJECTED] Módulo 2 rechazó la consulta de reservas de la habitación {}: HTTP {},"
                            + " errorCode={}",
                    query.roomId(),
                    ex.getStatusCode().value(),
                    errorCode(ex));
            throw new ExternalModule2UnavailableException(ex);
        } catch (RestClientException ex) {
            log.warn(
                    "Módulo 2 no respondió la consulta de reservas de la habitación {}: {}",
                    query.roomId(),
                    ex.getMessage());
            throw new ExternalModule2UnavailableException(ex);
        }
        if (response == null || response.items() == null) {
            log.warn("Módulo 2 respondió la consulta de reservas de la habitación {} sin items", query.roomId());
            throw new ExternalModule2UnavailableException(new IllegalStateException("Respuesta sin items"));
        }
        return new ReservationConflictResult(
                response.items().stream().map(ReservationItem::toDomain).toList());
    }

    /** {@code errorCode} del cuerpo de error de Módulo 2, o {@code null} si no se puede leer. */
    private static String errorCode(HttpClientErrorException ex) {
        try {
            Module2Error error = ex.getResponseBodyAs(Module2Error.class);
            return error == null ? null : error.errorCode();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Envoltura sin paginar de FR-023. Las habitaciones de cada reserva no se leen: ningún flujo las usa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReservationsResponse(List<ReservationItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReservationItem(String reservationRef, String status, LocalDate startDate, LocalDate endDate) {

        ConflictingReservation toDomain() {
            return new ConflictingReservation(reservationRef, status, startDate, endDate);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Module2Error(String errorCode) {}
}

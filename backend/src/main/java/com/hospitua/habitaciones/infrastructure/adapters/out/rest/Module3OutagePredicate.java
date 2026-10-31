package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import java.util.function.Predicate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Qué fallas cuentan para abrir el circuito {@code module3} ({@code record-failure-predicate} en
 * {@code application.yml}): que Módulo 3 no responda a tiempo, no se pueda conectar o responda 5xx. Un 4xx es una
 * respuesta de negocio de una estancia (p. ej. {@code RESERVATION_NOT_FOUND}) y no debe cortar la consulta de las
 * demás.
 */
public class Module3OutagePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof RestClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        return cause instanceof ResourceAccessException;
    }
}

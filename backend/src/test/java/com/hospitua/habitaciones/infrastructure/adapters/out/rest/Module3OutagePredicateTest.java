package com.hospitua.habitaciones.infrastructure.adapters.out.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class Module3OutagePredicateTest {

    private final Module3OutagePredicate predicate = new Module3OutagePredicate();

    @Test
    @DisplayName("T023: timeout, error de red y 5xx cuentan para abrir el circuito")
    void outages_count() {
        assertThat(predicate.test(failure("TIMEOUT", new ResourceAccessException("timeout"))))
                .isTrue();
        assertThat(predicate.test(failure(
                        "HTTP_ERROR",
                        HttpServerErrorException.create(
                                HttpStatus.SERVICE_UNAVAILABLE, "x", HttpHeaders.EMPTY, null, StandardCharsets.UTF_8))))
                .isTrue();
    }

    @Test
    @DisplayName("T023: un 4xx de negocio y una liquidación ilegible no cuentan")
    void businessAnswers_doNotCount() {
        assertThat(predicate.test(failure(
                        "RESERVATION_NOT_FOUND",
                        HttpClientErrorException.create(
                                HttpStatus.NOT_FOUND, "x", HttpHeaders.EMPTY, null, StandardCharsets.UTF_8))))
                .isFalse();
        assertThat(predicate.test(failure("INVALID_RESPONSE", new IllegalArgumentException("abc"))))
                .isFalse();
    }

    private static ExternalModule3UnavailableException failure(String cause, Throwable source) {
        return new ExternalModule3UnavailableException(cause, source);
    }
}

package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Arma el cuerpo {@link ApiError} con la hora del servidor (regla 6) y la ruta de la petición.
 */
@Component
@RequiredArgsConstructor
class ApiErrorFactory {

    private final Clock clock;

    ApiError create(String errorCode, String message, HttpServletRequest request, Map<String, Object> details) {
        return new ApiError(
                errorCode,
                message,
                OffsetDateTime.now(clock),
                request.getRequestURI(),
                details.isEmpty() ? null : details);
    }
}

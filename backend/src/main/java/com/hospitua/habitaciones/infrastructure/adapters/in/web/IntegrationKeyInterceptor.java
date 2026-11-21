package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.domain.exception.IntegrationUnauthorizedException;
import com.hospitua.habitaciones.infrastructure.config.IntegrationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Exige la llave de integración en las rutas de cada módulo cliente (plan de integración, Fase 1). Compara el método y
 * el patrón de la ruta del controller ({@code GET /api/rooms/{roomId}/base-rate}), así que una ruta con el mismo
 * prefijo que usa el frontend ({@code POST /api/rooms}) no se ve afectada. La llave se compara en tiempo constante y
 * nunca se escribe en el log. Sin llave configurada para un cliente, sus rutas siguen abiertas.
 */
@Slf4j
@Component
public class IntegrationKeyInterceptor implements HandlerInterceptor {

    /** Ruta protegida ({@code "GET /patrón"}) → cliente y su llave. */
    private final Map<String, ClientKey> keysByRoute = new HashMap<>();

    public IntegrationKeyInterceptor(IntegrationProperties properties) {
        properties.clients().forEach((client, config) -> {
            if (!config.enforced()) {
                log.warn(
                        "El cliente de integración {} no tiene llave: sus rutas {} quedan abiertas",
                        client,
                        config.routes());
                return;
            }
            byte[] key = config.key().getBytes(StandardCharsets.UTF_8);
            config.routes().forEach(route -> keysByRoute.put(route, new ClientKey(client, key)));
        });
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        ClientKey expected = pattern == null ? null : keysByRoute.get(request.getMethod() + " " + pattern);
        if (expected == null) {
            return true;
        }
        String received = request.getHeader(IntegrationProperties.KEY_HEADER);
        if (received == null || !MessageDigest.isEqual(expected.key(), received.getBytes(StandardCharsets.UTF_8))) {
            log.warn(
                    "Consulta de integración rechazada en {} {}: llave ausente o incorrecta",
                    request.getMethod(),
                    pattern);
            throw new IntegrationUnauthorizedException();
        }
        log.debug("Consulta de integración de {} en {} {}", expected.client(), request.getMethod(), pattern);
        return true;
    }

    private record ClientKey(String client, byte[] key) {}
}

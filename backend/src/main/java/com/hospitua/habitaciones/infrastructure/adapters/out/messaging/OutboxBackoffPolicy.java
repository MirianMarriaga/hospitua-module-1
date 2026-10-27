package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Política de espera creciente para reintentos del Outbox.
 * Progresión: 1s, 2s, 4s, 8s... con tope máximo de 5 minutos (300 segundos).
 */
@Component
public class OutboxBackoffPolicy {

    private static final long MAX_DELAY_SECONDS = 300; // 5 minutos

    /**
     * Calcula la duración de espera antes del siguiente intento según el número de reintento.
     *
     * @param retryCount número de reintento (1 para el primer fallo, 2 para el segundo, etc.)
     * @return Duration con el tiempo de espera calculado
     */
    public Duration calculateDelay(int retryCount) {
        if (retryCount <= 1) {
            return Duration.ofSeconds(1);
        }
        // 2^(retryCount - 1) segundos, con tope de 300 segundos
        long delaySeconds = (long) Math.pow(2, retryCount - 1);
        if (delaySeconds < 0 || delaySeconds > MAX_DELAY_SECONDS) {
            delaySeconds = MAX_DELAY_SECONDS;
        }
        return Duration.ofSeconds(delaySeconds);
    }
}

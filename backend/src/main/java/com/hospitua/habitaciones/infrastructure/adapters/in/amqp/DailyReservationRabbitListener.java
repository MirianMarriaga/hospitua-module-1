package com.hospitua.habitaciones.infrastructure.adapters.in.amqp;

import com.hospitua.habitaciones.application.dto.DailyReservationListMessageDto;
import com.hospitua.habitaciones.application.dto.DailyReservationUpdateMessageDto;
import com.hospitua.habitaciones.application.ports.in.IngestDailyReservationsUseCase;
import com.hospitua.habitaciones.domain.model.DailyReservationIngestionOutcome;
import com.hospitua.habitaciones.infrastructure.adapters.in.amqp.dto.DailyReservationListMessage;
import com.hospitua.habitaciones.infrastructure.adapters.in.amqp.dto.DailyReservationUpdateMessage;
import com.hospitua.habitaciones.infrastructure.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor de {@code m1.reservas.diarias.queue} (plan-consultar-reservas.md T014). Lee y valida el mensaje según su
 * routing key; uno ilegible o incompleto va a la cola de fallidos sin aplicarse. Un fallo al aplicarlo se reintenta
 * con espera creciente y, agotados los reintentos, también va a la cola de fallidos ({@link RabbitConfig}).
 */
@Component
@RequiredArgsConstructor
public class DailyReservationRabbitListener {

    /** Id del contenedor del listener, para detenerlo en las pruebas que leen la cola directamente. */
    public static final String LISTENER_ID = "dailyReservationsListener";

    private static final Logger log = LoggerFactory.getLogger(DailyReservationRabbitListener.class);

    private final IngestDailyReservationsUseCase ingestDailyReservations;
    private final JsonMapper jsonMapper;

    @RabbitListener(
            id = LISTENER_ID,
            queues = RabbitConfig.DAILY_RESERVATIONS_QUEUE,
            containerFactory = RabbitConfig.DAILY_RESERVATIONS_LISTENER_FACTORY)
    public void onMessage(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        DailyReservationIngestionOutcome outcome;
        String messageId;
        if (RabbitConfig.ROUTING_KEY_DAILY_LIST.equals(routingKey)) {
            DailyReservationListMessageDto list =
                    read(message, DailyReservationListMessage.class).toDto();
            messageId = list.messageId();
            outcome = ingestDailyReservations.ingestDailyList(list);
        } else if (RabbitConfig.ROUTING_KEY_DAILY_UPDATE.equals(routingKey)) {
            DailyReservationUpdateMessageDto update =
                    read(message, DailyReservationUpdateMessage.class).toDto();
            messageId = update.messageId();
            outcome = ingestDailyReservations.ingestUpdate(update);
        } else {
            throw new InvalidDailyReservationMessageException("Routing key desconocida: " + routingKey + ".");
        }
        log.info("Mensaje {} de la cola diaria ({}): {}", messageId, routingKey, outcome);
    }

    private <T> T read(Message message, Class<T> type) {
        try {
            return jsonMapper.readValue(message.getBody(), type);
        } catch (RuntimeException ex) {
            throw new InvalidDailyReservationMessageException("Mensaje ilegible de la cola diaria.", ex);
        }
    }
}

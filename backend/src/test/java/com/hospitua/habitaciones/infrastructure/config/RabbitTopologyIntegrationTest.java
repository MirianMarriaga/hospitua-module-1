package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.infrastructure.adapters.in.amqp.DailyReservationRabbitListener;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RabbitTopologyIntegrationTest {

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Test
    @DisplayName("La topología completa de RabbitMQ se declara correctamente")
    void topologyShouldBeDeclared() {
        QueueInformation checkInQueue = amqpAdmin.getQueueInfo(RabbitConfig.CHECKIN_QUEUE);
        assertThat(checkInQueue).isNotNull();

        QueueInformation checkOutQueue = amqpAdmin.getQueueInfo(RabbitConfig.CHECKOUT_QUEUE);
        assertThat(checkOutQueue).isNotNull();

        QueueInformation maintenanceQueue = amqpAdmin.getQueueInfo(RabbitConfig.MAINTENANCE_QUEUE);
        assertThat(maintenanceQueue).isNotNull();

        QueueInformation dailyReservationsQueue = amqpAdmin.getQueueInfo(RabbitConfig.DAILY_RESERVATIONS_QUEUE);
        assertThat(dailyReservationsQueue).isNotNull();

        QueueInformation dlq = amqpAdmin.getQueueInfo(RabbitConfig.DAILY_RESERVATIONS_DLQ);
        assertThat(dlq).isNotNull();
    }

    @Test
    @DisplayName("Un mensaje con cada routing key de salida llega a su cola correspondiente")
    void outgoingMessagesShouldRouteToRespectiveQueues() {
        // 1. Check-In
        Map<String, Object> checkInMsg = Map.of("test", "checkin");
        rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.ROUTING_KEY_CHECKIN, checkInMsg);
        Object receivedCheckIn = rabbitTemplate.receiveAndConvert(RabbitConfig.CHECKIN_QUEUE, 5000);
        assertThat(receivedCheckIn).isNotNull();

        // 2. Check-Out
        Map<String, Object> checkOutMsg = Map.of("test", "checkout");
        rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.ROUTING_KEY_CHECKOUT, checkOutMsg);
        Object receivedCheckOut = rabbitTemplate.receiveAndConvert(RabbitConfig.CHECKOUT_QUEUE, 5000);
        assertThat(receivedCheckOut).isNotNull();

        // 3. Mantenimiento
        Map<String, Object> maintenanceMsg = Map.of("test", "mantenimiento");
        rabbitTemplate.convertAndSend(
                RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.ROUTING_KEY_MAINTENANCE, maintenanceMsg);
        Object receivedMaintenance = rabbitTemplate.receiveAndConvert(RabbitConfig.MAINTENANCE_QUEUE, 5000);
        assertThat(receivedMaintenance).isNotNull();
    }

    @Test
    @DisplayName("Mensajes con routing keys de entrada llegan a m1.reservas.diarias.queue")
    void incomingMessagesShouldRouteToDailyReservationsQueue() {
        // Sin el listener de la cola diaria, que si no consumiría los mensajes de prueba
        MessageListenerContainer listener =
                listenerRegistry.getListenerContainer(DailyReservationRabbitListener.LISTENER_ID);
        listener.stop();
        try {
            // Lista del día
            Map<String, Object> dailyListMsg = Map.of("test", "dailyList");
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.ROUTING_KEY_DAILY_LIST, dailyListMsg);
            Object receivedList = rabbitTemplate.receiveAndConvert(RabbitConfig.DAILY_RESERVATIONS_QUEUE, 5000);
            assertThat(receivedList).isNotNull();

            // Actualización
            Map<String, Object> updateMsg = Map.of("test", "update");
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.ROUTING_KEY_DAILY_UPDATE, updateMsg);
            Object receivedUpdate = rabbitTemplate.receiveAndConvert(RabbitConfig.DAILY_RESERVATIONS_QUEUE, 5000);
            assertThat(receivedUpdate).isNotNull();
        } finally {
            listener.start();
        }
    }

    @Test
    @DisplayName("Un mensaje con routing key sin cola (facturacion.checkout) es devuelto con mandatory=true")
    void unroutableMessageShouldBeReturnedWithMandatory() throws Exception {
        CorrelationData correlationData = new CorrelationData("unroutable-test-id");
        Map<String, Object> unroutableMsg = Map.of("test", "facturacion");

        rabbitTemplate.convertAndSend(
                RabbitConfig.EVENTS_EXCHANGE,
                RabbitConfig.ROUTING_KEY_BILLING_CHECKOUT,
                unroutableMsg,
                correlationData);

        // Esperar la confirmación y la devolución
        CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
        assertThat(confirm).isNotNull();

        // Con mandatory = true y sin cola enlazada, RabbitMQ devuelve el mensaje
        ReturnedMessage returned = correlationData.getReturned();
        if (returned == null) {
            // En versiones recientes de Spring AMQP, getReturned() se puebla asíncronamente
            // Damos una pequeña espera si hace falta
            CompletableFuture<ReturnedMessage> returnedFuture = new CompletableFuture<>();
            for (int i = 0; i < 20; i++) {
                if (correlationData.getReturned() != null) {
                    returnedFuture.complete(correlationData.getReturned());
                    break;
                }
                Thread.sleep(50);
            }
            returned = returnedFuture.getNow(null);
        }

        assertThat(returned).isNotNull();
        assertThat(returned.getRoutingKey()).isEqualTo(RabbitConfig.ROUTING_KEY_BILLING_CHECKOUT);
    }
}

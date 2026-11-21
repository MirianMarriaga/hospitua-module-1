package com.hospitua.habitaciones.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Las colas de Módulo 2 se declaran solo cuando la propiedad lo permite (en producción las declara Módulo 2). */
class PartnerQueuesConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TopicExchange.class, () -> new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
            .withUserConfiguration(PartnerQueuesConfig.class);

    @Test
    @DisplayName("Sin la propiedad se declaran las tres colas de Módulo 2 (desarrollo y pruebas)")
    void declaresPartnerQueuesByDefault() {
        runner.run(context -> assertThat(context.getBeansOfType(Queue.class).values())
                .extracting(Queue::getName)
                .containsExactlyInAnyOrder(
                        RabbitConfig.CHECKIN_QUEUE, RabbitConfig.CHECKOUT_QUEUE, RabbitConfig.MAINTENANCE_QUEUE));
    }

    @Test
    @DisplayName("Con declare-partner-queues=false no se declara ninguna cola de Módulo 2 (producción)")
    void skipsPartnerQueuesWhenDisabled() {
        runner.withPropertyValues("hospitua.messaging.declare-partner-queues=false")
                .run(context -> assertThat(context.getBeansOfType(Queue.class)).isEmpty());
    }
}

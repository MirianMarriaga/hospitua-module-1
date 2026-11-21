package com.hospitua.habitaciones.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas de Módulo 2 declaradas por este backend, para desarrollo y pruebas sin Módulo 2. En producción las declara
 * Módulo 2 con sus propios argumentos (dead-letter hacia sus {@code .dlq}), y declararlas aquí sin ellos haría
 * fallar su declaración (PRECONDITION_FAILED): se apagan con
 * {@code hospitua.messaging.declare-partner-queues=false}.
 */
@Configuration
@ConditionalOnProperty(name = "hospitua.messaging.declare-partner-queues", havingValue = "true", matchIfMissing = true)
public class PartnerQueuesConfig {

    @Bean
    public Queue checkInQueue() {
        return QueueBuilder.durable(RabbitConfig.CHECKIN_QUEUE).build();
    }

    @Bean
    public Binding checkInBinding(Queue checkInQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(checkInQueue).to(eventsExchange).with(RabbitConfig.ROUTING_KEY_CHECKIN);
    }

    @Bean
    public Queue checkOutQueue() {
        return QueueBuilder.durable(RabbitConfig.CHECKOUT_QUEUE).build();
    }

    @Bean
    public Binding checkOutBinding(Queue checkOutQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(checkOutQueue).to(eventsExchange).with(RabbitConfig.ROUTING_KEY_CHECKOUT);
    }

    @Bean
    public Queue maintenanceQueue() {
        return QueueBuilder.durable(RabbitConfig.MAINTENANCE_QUEUE).build();
    }

    @Bean
    public Binding maintenanceBinding(Queue maintenanceQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(maintenanceQueue).to(eventsExchange).with(RabbitConfig.ROUTING_KEY_MAINTENANCE);
    }
}

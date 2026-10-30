package com.hospitua.habitaciones.infrastructure.config;

import java.time.Duration;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

/**
 * Configuración de la topología de RabbitMQ para el Módulo 1.
 * Define exchanges, colas durables, bindings, DLX/DLQ y convertidor JSON.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "hospitua.events";
    public static final String EVENTS_DLX = "hospitua.events.dlx";

    public static final String CHECKIN_QUEUE = "m2.habitacion.checkin.queue";
    public static final String CHECKOUT_QUEUE = "m2.habitacion.checkout.queue";
    public static final String MAINTENANCE_QUEUE = "m2.habitacion.mantenimiento.queue";

    public static final String DAILY_RESERVATIONS_QUEUE = "m1.reservas.diarias.queue";
    public static final String DAILY_RESERVATIONS_DLQ = "m1.reservas.diarias.dlq";
    public static final String DAILY_RESERVATIONS_LISTENER_FACTORY = "dailyReservationsListenerFactory";

    public static final String ROUTING_KEY_CHECKIN = "habitacion.checkin";
    public static final String ROUTING_KEY_CHECKOUT = "habitacion.checkout";
    public static final String ROUTING_KEY_MAINTENANCE = "habitacion.mantenimiento";
    public static final String ROUTING_KEY_BILLING_CHECKOUT = "facturacion.checkout";

    public static final String ROUTING_KEY_DAILY_LIST = "reserva.lista-del-dia";
    public static final String ROUTING_KEY_DAILY_UPDATE = "reserva.lista-del-dia.actualizacion";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EVENTS_DLX, true, false);
    }

    @Bean
    public Queue dailyReservationsDlq() {
        return QueueBuilder.durable(DAILY_RESERVATIONS_DLQ).build();
    }

    @Bean
    public Binding dailyReservationsDlqBinding(Queue dailyReservationsDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(dailyReservationsDlq).to(deadLetterExchange).with(DAILY_RESERVATIONS_DLQ);
    }

    @Bean
    public Queue checkInQueue() {
        return QueueBuilder.durable(CHECKIN_QUEUE).build();
    }

    @Bean
    public Binding checkInBinding(Queue checkInQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(checkInQueue).to(eventsExchange).with(ROUTING_KEY_CHECKIN);
    }

    @Bean
    public Queue checkOutQueue() {
        return QueueBuilder.durable(CHECKOUT_QUEUE).build();
    }

    @Bean
    public Binding checkOutBinding(Queue checkOutQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(checkOutQueue).to(eventsExchange).with(ROUTING_KEY_CHECKOUT);
    }

    @Bean
    public Queue maintenanceQueue() {
        return QueueBuilder.durable(MAINTENANCE_QUEUE).build();
    }

    @Bean
    public Binding maintenanceBinding(Queue maintenanceQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(maintenanceQueue).to(eventsExchange).with(ROUTING_KEY_MAINTENANCE);
    }

    @Bean
    public Queue dailyReservationsQueue() {
        return QueueBuilder.durable(DAILY_RESERVATIONS_QUEUE)
                .withArgument("x-dead-letter-exchange", EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", DAILY_RESERVATIONS_DLQ)
                .build();
    }

    @Bean
    public Binding dailyReservationsDailyListBinding(Queue dailyReservationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(dailyReservationsQueue).to(eventsExchange).with(ROUTING_KEY_DAILY_LIST);
    }

    @Bean
    public Binding dailyReservationsDailyUpdateBinding(Queue dailyReservationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(dailyReservationsQueue).to(eventsExchange).with(ROUTING_KEY_DAILY_UPDATE);
    }

    /**
     * Fábrica del listener de la cola diaria (plan-consultar-reservas.md T014): un error temporal se reintenta con
     * espera creciente y, agotados los reintentos, el mensaje se rechaza sin reencolar y el Dead-Letter Exchange lo
     * lleva a {@code m1.reservas.diarias.dlq}. Un mensaje inválido ({@link AmqpRejectAndDontRequeueException}) va a
     * esa cola sin reintentos.
     */
    @Bean(name = DAILY_RESERVATIONS_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory dailyReservationsListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Value("${hospitua.daily-reservations.retry.max-retries:3}") long maxRetries,
            @Value("${hospitua.daily-reservations.retry.initial-interval:1s}") Duration initialInterval,
            @Value("${hospitua.daily-reservations.retry.multiplier:2}") double multiplier,
            @Value("${hospitua.daily-reservations.retry.max-interval:10s}") Duration maxInterval) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(maxRetries)
                .delay(initialInterval)
                .multiplier(multiplier)
                .maxDelay(maxInterval)
                .predicate(RabbitConfig::isTemporaryFailure)
                .build();
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    /** Un mensaje rechazado a propósito no se reintenta: el error está en el mensaje, no en el entorno. */
    private static boolean isTemporaryFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AmqpRejectAndDontRequeueException) {
                return false;
            }
        }
        return true;
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new org.springframework.amqp.support.converter.JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonMessageConverter);
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }
}

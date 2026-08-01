package com.settleup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology:
 *  - One topic exchange: settleup.exchange
 *  - Three durable queues with dead-letter routing
 *    1. settlement.process.queue
 *    2. notification.dispatch.queue
 *    3. budget.alert.queue
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchanges.settleup}")
    private String exchangeName;

    @Value("${app.rabbitmq.queues.settlement-process}")
    private String settlementQueue;

    @Value("${app.rabbitmq.queues.notification-dispatch}")
    private String notificationQueue;

    @Value("${app.rabbitmq.queues.budget-alert}")
    private String budgetQueue;

    @Value("${app.rabbitmq.routing-keys.settlement}")
    private String settlementRoutingKey;

    @Value("${app.rabbitmq.routing-keys.notification}")
    private String notificationRoutingKey;

    @Value("${app.rabbitmq.routing-keys.budget}")
    private String budgetRoutingKey;

    // ── Exchange ─────────────────────────────────────────────────
    @Bean
    public TopicExchange settleUpExchange() {
        return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
    }

    // ── Queues ───────────────────────────────────────────────────
    @Bean
    public Queue settlementProcessQueue() {
        return QueueBuilder.durable(settlementQueue).build();
    }

    @Bean
    public Queue notificationDispatchQueue() {
        return QueueBuilder.durable(notificationQueue).build();
    }

    @Bean
    public Queue budgetAlertQueue() {
        return QueueBuilder.durable(budgetQueue).build();
    }

    // ── Bindings ─────────────────────────────────────────────────
    @Bean
    public Binding settlementBinding() {
        return BindingBuilder.bind(settlementProcessQueue())
                .to(settleUpExchange())
                .with(settlementRoutingKey);
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationDispatchQueue())
                .to(settleUpExchange())
                .with(notificationRoutingKey);
    }

    @Bean
    public Binding budgetBinding() {
        return BindingBuilder.bind(budgetAlertQueue())
                .to(settleUpExchange())
                .with(budgetRoutingKey);
    }

    // ── Message serialisation (JSON) ──────────────────────────────
    @Bean
    public MessageConverter jacksonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}

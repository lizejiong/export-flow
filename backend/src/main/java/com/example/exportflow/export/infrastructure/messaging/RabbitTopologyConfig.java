package com.example.exportflow.export.infrastructure.messaging;

import com.example.exportflow.common.config.ExportProperties;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {
    public static final String TASK_EXCHANGE = "export.task.exchange";
    public static final String TASK_QUEUE = "export.task.queue";
    public static final String TASK_ROUTING_KEY = "export.task.execute";
    public static final String RETRY_EXCHANGE = "export.retry.exchange";
    public static final String RETRY_QUEUE = "export.retry.queue";
    public static final String RETRY_ROUTING_KEY = "export.task.retry";
    public static final String DEAD_EXCHANGE = "export.dead.exchange";
    public static final String DEAD_QUEUE = "export.dead.queue";
    public static final String DEAD_ROUTING_KEY = "export.task.dead";

    @Bean DirectExchange taskExchange() { return new DirectExchange(TASK_EXCHANGE, true, false); }
    @Bean DirectExchange retryExchange() { return new DirectExchange(RETRY_EXCHANGE, true, false); }
    @Bean DirectExchange deadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }

    @Bean Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE).deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_ROUTING_KEY).build();
    }

    @Bean Queue retryQueue(ExportProperties properties) {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(Math.toIntExact(properties.retryDelay().toMillis()))
                .deadLetterExchange(TASK_EXCHANGE)
                .deadLetterRoutingKey(TASK_ROUTING_KEY)
                .build();
    }

    @Bean Queue deadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding taskBinding(Queue taskQueue, DirectExchange taskExchange) { return BindingBuilder.bind(taskQueue).to(taskExchange).with(TASK_ROUTING_KEY); }
    @Bean Binding retryBinding(Queue retryQueue, DirectExchange retryExchange) { return BindingBuilder.bind(retryQueue).to(retryExchange).with(RETRY_ROUTING_KEY); }
    @Bean Binding deadBinding(Queue deadQueue, DirectExchange deadExchange) { return BindingBuilder.bind(deadQueue).to(deadExchange).with(DEAD_ROUTING_KEY); }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

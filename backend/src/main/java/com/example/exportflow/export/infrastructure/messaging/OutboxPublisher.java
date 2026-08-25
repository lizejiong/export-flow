package com.example.exportflow.export.infrastructure.messaging;

import com.example.exportflow.export.domain.OutboxEvent;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxPublisher(OutboxMapper outboxMapper, RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper, Clock clock) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.rabbitTemplate.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${app.export.outbox-interval:1s}")
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<OutboxEvent> events = outboxMapper.findPending(now, 100);
        for (OutboxEvent event : events) publish(event);
    }

    private void publish(OutboxEvent event) {
        try {
            ExportTaskMessage message = objectMapper.readValue(event.payload(), ExportTaskMessage.class);
            if (!message.isSupported()) throw new IllegalArgumentException("Unsupported export message schema");
            Route route = route(event.eventType());
            CorrelationData correlation = new CorrelationData(event.eventId());
            rabbitTemplate.convertAndSend(route.exchange(), route.routingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("RabbitMQ nack: " + confirm.getReason());
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned unroutable message: "
                        + correlation.getReturned().getReplyText());
            }
            outboxMapper.markPublished(event.id(), LocalDateTime.now(clock));
        } catch (Exception exception) {
            String message = abbreviate(exception.getMessage());
            long backoffSeconds = Math.min(300, 1L << Math.min(8, event.publishAttempts()));
            outboxMapper.markFailed(event.id(), LocalDateTime.now(clock).plusSeconds(backoffSeconds), message);
            log.warn("Outbox publish failed, eventId={}", event.eventId(), exception);
        }
    }

    private Route route(String eventType) {
        return switch (eventType) {
            case "EXPORT_TASK_CREATED" -> new Route(RabbitTopologyConfig.TASK_EXCHANGE,
                    RabbitTopologyConfig.TASK_ROUTING_KEY);
            case "EXPORT_TASK_RETRY", "EXPORT_TASK_RECOVER" -> new Route(RabbitTopologyConfig.RETRY_EXCHANGE,
                    RabbitTopologyConfig.RETRY_ROUTING_KEY);
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
        };
    }

    private String abbreviate(String message) {
        if (message == null) return "unknown publish error";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record Route(String exchange, String routingKey) {
    }
}

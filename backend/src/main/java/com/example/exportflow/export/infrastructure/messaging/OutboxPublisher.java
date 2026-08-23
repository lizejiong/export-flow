package com.example.exportflow.export.infrastructure.messaging;

import com.example.exportflow.export.domain.OutboxEvent;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final OutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxMapper outboxMapper, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.export.outbox-interval:1s}")
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<OutboxEvent> events = outboxMapper.findPending(now, 100);
        for (OutboxEvent event : events) publish(event);
    }

    private void publish(OutboxEvent event) {
        try {
            ExportTaskMessage message = objectMapper.readValue(event.payload(), ExportTaskMessage.class);
            boolean retry = event.eventType().contains("RETRY") || event.eventType().contains("RECOVER");
            String exchange = retry ? RabbitTopologyConfig.RETRY_EXCHANGE : RabbitTopologyConfig.TASK_EXCHANGE;
            String routingKey = retry ? RabbitTopologyConfig.RETRY_ROUTING_KEY : RabbitTopologyConfig.TASK_ROUTING_KEY;
            CorrelationData correlation = new CorrelationData(event.eventId());
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("RabbitMQ nack: " + confirm.getReason());
            outboxMapper.markPublished(event.id(), LocalDateTime.now(ZONE));
        } catch (Exception exception) {
            String message = abbreviate(exception.getMessage());
            long backoffSeconds = Math.min(300, 1L << Math.min(8, event.publishAttempts()));
            outboxMapper.markFailed(event.id(), LocalDateTime.now(ZONE).plusSeconds(backoffSeconds), message);
            log.warn("Outbox publish failed, eventId={}: {}", event.eventId(), message);
        }
    }

    private String abbreviate(String message) {
        if (message == null) return "unknown publish error";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}

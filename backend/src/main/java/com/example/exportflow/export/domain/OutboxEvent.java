package com.example.exportflow.export.domain;

import java.time.LocalDateTime;

public record OutboxEvent(
        long id,
        String eventId,
        long aggregateId,
        String eventType,
        String payload,
        OutboxStatus status,
        int publishAttempts,
        LocalDateTime nextRetryAt,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        String lastError
) {
}


package com.example.exportflow.export.infrastructure.progress;

import java.time.LocalDateTime;

public record TaskProgressEvent(
        String eventType,
        long taskId,
        String status,
        String stage,
        int progress,
        long expectedCount,
        long exportedCount,
        Long fileSize,
        LocalDateTime fileExpireAt,
        LocalDateTime updatedAt
) {
}

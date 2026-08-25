package com.example.exportflow.export.infrastructure.progress;

import java.time.LocalDateTime;

public record TaskProgressEvent(
        String eventType,
        long taskId,
        long version,
        String requestId,
        String status,
        String stage,
        int currentRunNo,
        int progress,
        long expectedCount,
        long exportedCount,
        Long fileSize,
        LocalDateTime fileExpireAt,
        LocalDateTime updatedAt
) {
    public static TaskProgressEvent from(com.example.exportflow.export.domain.ExportTask task,
                                         String eventType, String requestId) {
        return new TaskProgressEvent(eventType, task.getId(), task.getVersion(), requestId,
                task.getStatus().name(), task.getStage().name(), task.getCurrentRunNo(), task.getProgress(),
                task.getExpectedCount(), task.getExportedCount(), task.getFileSize(), task.getFileExpireAt(),
                task.getUpdatedAt());
    }
}

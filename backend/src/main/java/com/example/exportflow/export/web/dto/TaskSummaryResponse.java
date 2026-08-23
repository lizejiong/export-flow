package com.example.exportflow.export.web.dto;

import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;

import java.time.LocalDateTime;

public record TaskSummaryResponse(
        long id,
        String taskNo,
        ExportType exportType,
        ExportTaskStatus status,
        ExportStage stage,
        long expectedCount,
        long exportedCount,
        int progress,
        Long fileSize,
        LocalDateTime fileExpireAt,
        int autoAttemptCount,
        int manualRetryIndex,
        boolean retryable,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static TaskSummaryResponse from(ExportTask task) {
        return new TaskSummaryResponse(task.getId(), task.getTaskNo(), task.getExportType(), task.getStatus(),
                task.getStage(), task.getExpectedCount(), task.getExportedCount(), task.getProgress(),
                task.getFileSize(), task.getFileExpireAt(), task.getAutoAttemptCount(), task.getManualRetryIndex(),
                task.isRetryable(), task.getFailureCode(), task.getFailureMessage(), task.getCreatedAt(),
                task.getStartedAt(), task.getCompletedAt());
    }
}


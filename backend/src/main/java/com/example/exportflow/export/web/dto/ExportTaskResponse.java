package com.example.exportflow.export.web.dto;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;

import java.time.LocalDateTime;

public record ExportTaskResponse(
        long id,
        String taskNo,
        ExportType exportType,
        ExportTaskStatus status,
        long expectedCount,
        int progress,
        LocalDateTime createdAt,
        boolean idempotentReplay
) {
    public static ExportTaskResponse from(ExportTask task, boolean replay) {
        return new ExportTaskResponse(task.getId(), task.getTaskNo(), task.getExportType(), task.getStatus(),
                task.getExpectedCount(), task.getProgress(), task.getCreatedAt(), replay);
    }
}


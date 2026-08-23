package com.example.exportflow.export.domain;

import java.time.LocalDateTime;

public record ExportTaskAttempt(
        Long id,
        long taskId,
        int attemptNo,
        String executionToken,
        String workerId,
        AttemptStatus status,
        LocalDateTime startedAt,
        LocalDateTime heartbeatAt,
        LocalDateTime finishedAt,
        String failureCode,
        String failureMessage
) {
}


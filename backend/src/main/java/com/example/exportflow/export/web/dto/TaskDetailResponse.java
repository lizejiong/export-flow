package com.example.exportflow.export.web.dto;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskAttempt;

import java.util.List;

public record TaskDetailResponse(
        TaskSummaryResponse task,
        String filterSnapshotJson,
        Long snapshotMaxId,
        String snapshotTime,
        long selectedCount,
        int downloadCount,
        String lastDownloadedAt,
        Long sourceTaskId,
        Long rootTaskId,
        List<AttemptResponse> attempts,
        List<TaskSummaryResponse> retryChain
) {
    public static TaskDetailResponse from(ExportTask task, long selectedCount, List<ExportTaskAttempt> attempts,
                                          List<ExportTask> chain) {
        return new TaskDetailResponse(TaskSummaryResponse.from(task), task.getFilterSnapshotJson(), task.getSnapshotMaxId(),
                task.getSnapshotTime() == null ? null : task.getSnapshotTime().toString(), selectedCount,
                task.getDownloadCount(), task.getLastDownloadedAt() == null ? null : task.getLastDownloadedAt().toString(),
                task.getSourceTaskId(), task.getRootTaskId(), attempts.stream().map(AttemptResponse::from).toList(),
                chain.stream().map(TaskSummaryResponse::from).toList());
    }

    public record AttemptResponse(
            int attemptNo, String executionTokenShort, String workerId, String status,
            String startedAt, String finishedAt, String failureCode, String failureMessage
    ) {
        static AttemptResponse from(ExportTaskAttempt attempt) {
            String token = attempt.executionToken();
            String shortToken = token == null ? null : token.substring(0, Math.min(12, token.length()));
            return new AttemptResponse(attempt.attemptNo(), shortToken, attempt.workerId(), attempt.status().name(),
                    attempt.startedAt().toString(), attempt.finishedAt() == null ? null : attempt.finishedAt().toString(),
                    attempt.failureCode(), attempt.failureMessage());
        }
    }
}


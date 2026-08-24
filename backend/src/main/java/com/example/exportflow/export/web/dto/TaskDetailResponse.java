package com.example.exportflow.export.web.dto;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskAttempt;
import com.example.exportflow.export.domain.ExportTaskRun;

import java.util.List;

public record TaskDetailResponse(
        TaskSummaryResponse task,
        String filterSnapshotJson,
        Long snapshotMaxId,
        String snapshotTime,
        long selectedCount,
        int downloadCount,
        String lastDownloadedAt,
        List<RunResponse> runs
) {
    public static TaskDetailResponse from(ExportTask task, long selectedCount, List<RunResponse> runs) {
        return new TaskDetailResponse(TaskSummaryResponse.from(task), task.getFilterSnapshotJson(), task.getSnapshotMaxId(),
                task.getSnapshotTime() == null ? null : task.getSnapshotTime().toString(), selectedCount,
                task.getDownloadCount(), task.getLastDownloadedAt() == null ? null : task.getLastDownloadedAt().toString(),
                runs);
    }

    public record RunResponse(
            long id,
            int runNo,
            String triggerType,
            String status,
            String stage,
            long expectedCount,
            long exportedCount,
            int progress,
            int autoAttemptCount,
            boolean retryable,
            String failureCode,
            String failureMessage,
            Long fileSize,
            String fileExpireAt,
            String createdAt,
            String startedAt,
            String completedAt,
            List<AttemptResponse> attempts
    ) {
        public static RunResponse from(ExportTaskRun run, List<ExportTaskAttempt> attempts) {
            return new RunResponse(run.getId(), run.getRunNo(), run.getTriggerType().name(), run.getStatus().name(),
                    run.getStage().name(), run.getExpectedCount(), run.getExportedCount(), run.getProgress(),
                    run.getAutoAttemptCount(), run.isRetryable(), run.getFailureCode(), run.getFailureMessage(),
                    run.getFileSize(), text(run.getFileExpireAt()), text(run.getCreatedAt()), text(run.getStartedAt()),
                    text(run.getCompletedAt()), attempts.stream().map(AttemptResponse::from).toList());
        }

        private static String text(Object value) { return value == null ? null : value.toString(); }
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

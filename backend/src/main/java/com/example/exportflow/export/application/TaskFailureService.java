package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskFailureService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ExportTaskMapper taskMapper;
    private final ExportAttemptMapper attemptMapper;
    private final ExportRunMapper runMapper;
    private final OutboxMapper outboxMapper;
    private final ExportFailureClassifier classifier;
    private final ExportProperties properties;
    private final ObjectMapper objectMapper;

    public TaskFailureService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper, ExportRunMapper runMapper,
                              OutboxMapper outboxMapper,
                              ExportFailureClassifier classifier, ExportProperties properties, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
        this.outboxMapper = outboxMapper;
        this.classifier = classifier;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(ExportTask task, Throwable throwable) {
        ExportFailureClassifier.Failure failure = classifier.classify(throwable);
        LocalDateTime now = LocalDateTime.now(ZONE);
        attemptMapper.markFailed(task.getExecutionToken(), "FAILED", failure.code(), failure.message(), now);
        if (failure.retryable() && task.getAutoAttemptCount() < properties.maxAutoAttempts()) {
            if (taskMapper.markRetryPending(task.getId(), task.getExecutionToken(), failure.code(), failure.message(), now) == 1) {
                requireCurrentRun(runMapper.markRetryPending(currentRunId(task), task.getExecutionToken(),
                        failure.code(), failure.message(), now));
                insertRetryEvent(task.getId(), now);
            }
        } else {
            if (taskMapper.markFailed(task.getId(), task.getExecutionToken(), failure.code(), failure.message(), failure.retryable(), now) == 1) {
                requireCurrentRun(runMapper.markFailed(currentRunId(task), task.getExecutionToken(),
                        failure.code(), failure.message(), failure.retryable(), now));
            }
        }
    }

    private long currentRunId(ExportTask task) {
        if (task.getCurrentRunId() == null) throw new IllegalStateException("Export task has no current run");
        return task.getCurrentRunId();
    }

    private void requireCurrentRun(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Cannot update current export run failure state");
        }
    }

    private void insertRetryEvent(long taskId, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId, "taskId", taskId, "eventType", "EXPORT_TASK_RETRY"));
            outboxMapper.insert(eventId, taskId, "EXPORT_TASK_RETRY", payload, now, now);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize retry event", exception);
        }
    }
}

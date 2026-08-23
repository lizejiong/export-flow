package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
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
    private final OutboxMapper outboxMapper;
    private final ExportFailureClassifier classifier;
    private final ExportProperties properties;
    private final ObjectMapper objectMapper;

    public TaskFailureService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper, OutboxMapper outboxMapper,
                              ExportFailureClassifier classifier, ExportProperties properties, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
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
                insertRetryEvent(task.getId(), now);
            }
        } else {
            taskMapper.markFailed(task.getId(), task.getExecutionToken(), failure.code(), failure.message(), failure.retryable(), now);
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

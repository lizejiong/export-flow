package com.example.exportflow.export.application;

import com.example.exportflow.common.api.RequestIdContext;
import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.example.exportflow.export.infrastructure.messaging.ExportTaskMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TaskFailureService {
    private final ExportTaskMapper taskMapper;
    private final ExportAttemptMapper attemptMapper;
    private final ExportRunMapper runMapper;
    private final OutboxMapper outboxMapper;
    private final ExportFailureClassifier classifier;
    private final ExportProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TaskFailureService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper, ExportRunMapper runMapper,
                              OutboxMapper outboxMapper,
                              ExportFailureClassifier classifier, ExportProperties properties, ObjectMapper objectMapper,
                              ApplicationEventPublisher events, Clock clock) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
        this.outboxMapper = outboxMapper;
        this.classifier = classifier;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public boolean handle(ExportTask task, Exception throwable) {
        ExportFailureClassifier.Failure failure = classifier.classify(throwable);
        LocalDateTime now = LocalDateTime.now(clock);
        if (failure.retryable() && task.getAutoAttemptCount() < properties.maxAutoAttempts()) {
            if (taskMapper.markRetryPending(task.getId(), task.getExecutionToken(), failure.code(), failure.message(), now) == 1) {
                requireCurrentRun(runMapper.markRetryPending(currentRunId(task), task.getExecutionToken(),
                        failure.code(), failure.message(), now));
                requireCurrentAttempt(attemptMapper.markFailed(task.getExecutionToken(), "FAILED",
                        failure.code(), failure.message(), now));
                insertRetryEvent(task.getId(), now);
                events.publishEvent(new TaskChangedEvent(task.getId(), "task.retrying", RequestIdContext.currentOrCreate()));
                return true;
            }
        } else {
            if (taskMapper.markFailed(task.getId(), task.getExecutionToken(), failure.code(), failure.message(), failure.retryable(), now) == 1) {
                requireCurrentRun(runMapper.markFailed(currentRunId(task), task.getExecutionToken(),
                        failure.code(), failure.message(), failure.retryable(), now));
                requireCurrentAttempt(attemptMapper.markFailed(task.getExecutionToken(), "FAILED",
                        failure.code(), failure.message(), now));
                events.publishEvent(new TaskChangedEvent(task.getId(), "task.failed", RequestIdContext.currentOrCreate()));
                return true;
            }
        }
        return false;
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

    private void requireCurrentAttempt(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Cannot update current export attempt failure state");
        }
    }

    private void insertRetryEvent(long taskId, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        try {
            String payload = objectMapper.writeValueAsString(ExportTaskMessage.current(eventId, taskId,
                    "EXPORT_TASK_RETRY", RequestIdContext.currentOrCreate()));
            outboxMapper.insert(eventId, taskId, "EXPORT_TASK_RETRY", payload, now, now);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize retry event", exception);
        }
    }
}

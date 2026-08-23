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
public class RecoveryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ExportTaskMapper taskMapper;
    private final ExportAttemptMapper attemptMapper;
    private final OutboxMapper outboxMapper;
    private final ExportProperties properties;
    private final ObjectMapper objectMapper;

    public RecoveryService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper, OutboxMapper outboxMapper,
                           ExportProperties properties, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recover(ExportTask task) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (task.getAutoAttemptCount() < properties.maxAutoAttempts()) {
            if (taskMapper.recoverStale(task.getId(), task.getExecutionToken(), task.getHeartbeatAt(), now) == 1) {
                attemptMapper.markFailed(task.getExecutionToken(), "LOST", "WORKER_LOST", "Worker 心跳超时", now);
                insertEvent(task.getId(), now);
            }
        } else {
            if (taskMapper.failStale(task.getId(), task.getExecutionToken(), task.getHeartbeatAt(), now) == 1) {
                attemptMapper.markFailed(task.getExecutionToken(), "LOST", "WORKER_LOST", "Worker 心跳超时", now);
            }
        }
    }

    private void insertEvent(long taskId, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId, "taskId", taskId, "eventType", "EXPORT_TASK_RECOVER"));
            outboxMapper.insert(eventId, taskId, "EXPORT_TASK_RECOVER", payload, now, now);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize recovery event", exception);
        }
    }
}

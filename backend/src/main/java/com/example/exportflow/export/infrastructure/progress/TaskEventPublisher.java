package com.example.exportflow.export.infrastructure.progress;

import com.example.exportflow.export.application.TaskChangedEvent;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class TaskEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(TaskEventPublisher.class);
    private final ExportTaskMapper taskMapper;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;

    public TaskEventPublisher(ExportTaskMapper taskMapper, ObjectProvider<StringRedisTemplate> redisProvider,
                              ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(TaskChangedEvent changed) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) return;
            ExportTask task = taskMapper.findById(changed.taskId());
            if (task == null || task.isArchived()) return;
            Map<String, String> snapshot = new HashMap<>();
            snapshot.put("status", task.getStatus().name());
            snapshot.put("stage", task.getStage().name());
            snapshot.put("progress", Integer.toString(task.getProgress()));
            snapshot.put("exportedCount", Long.toString(task.getExportedCount()));
            snapshot.put("version", Long.toString(task.getVersion()));
            snapshot.put("updatedAt", task.getUpdatedAt().toString());
            String key = "export:task:" + task.getId() + ":progress";
            redis.opsForHash().putAll(key, snapshot);
            redis.expire(key, Duration.ofHours(48));
            redis.convertAndSend(ProgressService.CHANNEL, objectMapper.writeValueAsString(
                    TaskProgressEvent.from(task, changed.eventType(), changed.requestId())));
        } catch (Exception exception) {
            log.warn("Redis task event publish failed, taskId={}, eventType={}",
                    changed.taskId(), changed.eventType(), exception);
        }
    }
}

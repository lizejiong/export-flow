package com.example.exportflow.export.infrastructure.progress;

import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
public class ProgressService {
    public static final String CHANNEL = "export:task:events";
    private static final Logger log = LoggerFactory.getLogger(ProgressService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ExportTaskMapper taskMapper;
    private final ExportAttemptMapper attemptMapper;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;

    public ProgressService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper, ObjectProvider<StringRedisTemplate> redisProvider,
                           ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
    }

    public boolean persist(ExportTask task, ExportStage stage, int progress, long exportedCount) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        int updated = taskMapper.updateProgress(task.getId(), task.getExecutionToken(), stage.name(), progress, exportedCount, now);
        if (updated == 1) {
            attemptMapper.heartbeat(task.getExecutionToken(), now);
            publish(task, "task.progress", stage, progress, exportedCount, now);
        }
        return updated == 1;
    }

    public void publishOnly(ExportTask task, ExportStage stage, int progress, long exportedCount) {
        publish(task, "task.progress", stage, progress, exportedCount, LocalDateTime.now(ZONE));
    }

    public void publish(ExportTask task, String eventType, ExportStage stage, int progress, long exportedCount, LocalDateTime now) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return;
        try {
            String key = "export:task:" + task.getId() + ":progress";
            redis.opsForHash().putAll(key, Map.of(
                    "status", task.getStatus().name(), "stage", stage.name(),
                    "progress", Integer.toString(progress), "exportedCount", Long.toString(exportedCount),
                    "updatedAt", now.toString()
            ));
            redis.expire(key, Duration.ofHours(48));
            TaskProgressEvent event = new TaskProgressEvent(eventType, task.getId(), task.getStatus().name(),
                    stage.name(), progress, task.getExpectedCount(), exportedCount,
                    task.getFileSize(), task.getFileExpireAt(), now);
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            log.warn("Redis progress publish failed for task {}: {}", task.getId(), exception.getMessage());
        }
    }
}

package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.example.exportflow.export.web.dto.ExportTaskResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class RetryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TASK_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final ExportTaskMapper taskMapper;
    private final OutboxMapper outboxMapper;
    private final ExportProperties properties;
    private final ObjectMapper objectMapper;

    public RetryService(ExportTaskMapper taskMapper, OutboxMapper outboxMapper,
                        ExportProperties properties, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportTaskResponse retry(long sourceTaskId, String idempotencyKey) {
        validateKey(idempotencyKey);
        String requestHash = retryHash(sourceTaskId);
        ExportTask replay = taskMapper.findByIdempotencyKey(idempotencyKey);
        if (replay != null) {
            if (!requestHash.equals(replay.getRequestHash())) throw reused();
            return ExportTaskResponse.from(replay, true);
        }
        ExportTask source = taskMapper.findById(sourceTaskId);
        if (source == null) throw new BusinessException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "导出任务不存在");
        if (source.getStatus() != ExportTaskStatus.FAILED || !source.isRetryable()) {
            throw new BusinessException("TASK_NOT_RETRYABLE", HttpStatus.CONFLICT, "当前任务不可手动重试");
        }
        long rootTaskId = source.getRootTaskId() == null ? source.getId() : source.getRootTaskId();
        int maxRetryIndex = taskMapper.findRetryChain(rootTaskId).stream()
                .mapToInt(ExportTask::getManualRetryIndex).max().orElse(0);
        if (maxRetryIndex >= properties.maxManualRetries()) {
            throw new BusinessException("MANUAL_RETRY_LIMIT_REACHED", HttpStatus.CONFLICT, "已达到手动重试次数上限");
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        ExportTask target = new ExportTask();
        target.setTaskNo("EXP" + TASK_TIME.format(now) + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        target.setIdempotencyKey(idempotencyKey);
        target.setRequestHash(requestHash);
        target.setExportType(source.getExportType());
        target.setStatus(ExportTaskStatus.PENDING);
        target.setStage(ExportStage.QUEUED);
        target.setFilterSnapshotJson(source.getFilterSnapshotJson());
        target.setSnapshotMaxId(source.getSnapshotMaxId());
        target.setSnapshotTime(source.getSnapshotTime());
        target.setExportFieldVersion(source.getExportFieldVersion());
        target.setExpectedCount(source.getExpectedCount());
        target.setManualRetryIndex(maxRetryIndex + 1);
        target.setSourceTaskId(source.getId());
        target.setRootTaskId(rootTaskId);
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        taskMapper.insert(target);
        if (source.getExportType() == ExportType.SELECTED) {
            taskMapper.copyItems(source.getId(), target.getId(), now);
        }
        insertEvent(target.getId(), now);
        return ExportTaskResponse.from(target, false);
    }

    private void insertEvent(long taskId, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId, "taskId", taskId, "eventType", "EXPORT_TASK_CREATED"));
            outboxMapper.insert(eventId, taskId, "EXPORT_TASK_CREATED", payload, now, now);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize retry task", exception);
        }
    }

    private String retryHash(long taskId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("retry:" + taskId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{8,64}")) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST, "Idempotency-Key 请求头缺失或格式错误");
        }
    }

    private BusinessException reused() {
        return new BusinessException("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT, "相同 Idempotency-Key 不能用于不同请求");
    }
}

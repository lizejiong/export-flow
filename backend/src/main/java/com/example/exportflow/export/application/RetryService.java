package com.example.exportflow.export.application;

import com.example.exportflow.common.api.RequestIdContext;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportRunTrigger;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskRun;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.example.exportflow.export.infrastructure.messaging.ExportTaskMessage;
import com.example.exportflow.export.web.dto.ExportTaskResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RetryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ExportTaskMapper taskMapper;
    private final ExportRunMapper runMapper;
    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RetryService(ExportTaskMapper taskMapper, ExportRunMapper runMapper, OutboxMapper outboxMapper,
                        ObjectMapper objectMapper) {
        this(taskMapper, runMapper, outboxMapper, objectMapper, ignored -> { }, Clock.system(ZONE), null);
    }

    @Autowired
    public RetryService(ExportTaskMapper taskMapper, ExportRunMapper runMapper, OutboxMapper outboxMapper,
                        ObjectMapper objectMapper, ApplicationEventPublisher events, Clock clock,
                        TransactionTemplate transactions) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.events = events;
        this.clock = clock;
        this.transactions = transactions;
    }

    public ExportTaskResponse retry(long sourceTaskId, String idempotencyKey) {
        validateKey(idempotencyKey);
        String requestHash = retryHash(sourceTaskId);
        ExportTaskRun existing = runMapper.findByIdempotencyKey(idempotencyKey);
        if (existing != null) return replay(sourceTaskId, requestHash, existing);
        try {
            if (transactions == null) return createRun(sourceTaskId, idempotencyKey, requestHash);
            ExportTaskResponse response = transactions.execute(status -> createRun(sourceTaskId, idempotencyKey, requestHash));
            if (response == null) throw new IllegalStateException("Cannot retry export task");
            return response;
        } catch (DuplicateKeyException duplicate) {
            ExportTaskRun winner = runMapper.findByIdempotencyKey(idempotencyKey);
            if (winner == null) throw duplicate;
            return replay(sourceTaskId, requestHash, winner);
        }
    }

    private ExportTaskResponse createRun(long sourceTaskId, String idempotencyKey, String requestHash) {
        ExportTask source = taskMapper.findByIdForUpdate(sourceTaskId);
        if (source == null || source.isArchived()) throw notFound();
        ExportTaskRun replay = runMapper.findByIdempotencyKeyForUpdate(idempotencyKey);
        if (replay != null) return replay(sourceTaskId, requestHash, replay);
        if (source.getStatus() != ExportTaskStatus.FAILED || !source.isRetryable()) {
            throw new BusinessException("TASK_NOT_RETRYABLE", HttpStatus.CONFLICT, "当前任务不可手动重试");
        }
        if (source.getCurrentRunId() == null) throw new IllegalStateException("Export task has no current run");
        if (source.getManualRetryCount() >= source.getManualRetryLimit()) {
            throw new BusinessException("MANUAL_RETRY_LIMIT_REACHED", HttpStatus.CONFLICT, "已达到手动重试次数上限");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int runNo = source.getManualRetryCount() + 1;
        ExportTaskRun run = new ExportTaskRun();
        run.setTaskId(source.getId());
        run.setRunNo(runNo);
        run.setTriggerType(ExportRunTrigger.MANUAL_RETRY);
        run.setIdempotencyKey(idempotencyKey);
        run.setRequestHash(requestHash);
        run.setStatus(ExportTaskStatus.PENDING);
        run.setStage(ExportStage.QUEUED);
        run.setExpectedCount(source.getExpectedCount());
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);
        if (taskMapper.startManualRun(source.getId(), source.getCurrentRunId(), run.getId(), runNo, now) != 1) {
            throw new BusinessException("TASK_RETRY_CONFLICT", HttpStatus.CONFLICT, "任务状态已变化，请刷新后重试");
        }
        source.setCurrentRunId(run.getId());
        source.setCurrentRunNo(runNo);
        source.setManualRetryCount(runNo);
        source.setManualRetryIndex(runNo);
        source.setStatus(ExportTaskStatus.PENDING);
        source.setStage(ExportStage.QUEUED);
        source.setAutoAttemptCount(0);
        source.setProgress(0);
        source.setExportedCount(0);
        source.setRetryable(false);
        source.setFailureCode(null);
        source.setFailureMessage(null);
        source.setUpdatedAt(now);
        insertEvent(source.getId(), now);
        events.publishEvent(new TaskChangedEvent(source.getId(), "task.retrying", RequestIdContext.currentOrCreate()));
        return ExportTaskResponse.from(source, false);
    }

    private ExportTaskResponse replay(long sourceTaskId, String requestHash, ExportTaskRun replay) {
        if (replay.getTaskId() != sourceTaskId || !requestHash.equals(replay.getRequestHash())) throw reused();
        ExportTask replayTask = taskMapper.findById(sourceTaskId);
        if (replayTask == null) throw notFound();
        return ExportTaskResponse.from(replayTask, true);
    }

    private void insertEvent(long taskId, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        try {
            String payload = objectMapper.writeValueAsString(ExportTaskMessage.current(eventId, taskId,
                    "EXPORT_TASK_CREATED", RequestIdContext.currentOrCreate()));
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
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key 请求头缺失或格式错误");
        }
    }

    private BusinessException reused() {
        return new BusinessException("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT,
                "相同 Idempotency-Key 不能用于不同请求");
    }

    private BusinessException notFound() {
        return new BusinessException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "导出任务不存在");
    }
}

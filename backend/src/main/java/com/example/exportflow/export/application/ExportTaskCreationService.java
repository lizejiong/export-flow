package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportRunTrigger;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskRun;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.example.exportflow.export.web.dto.CreateExportTaskRequest;
import com.example.exportflow.export.web.dto.ExportTaskResponse;
import com.example.exportflow.order.application.OrderFilter;
import com.example.exportflow.order.application.CountQuerySupport;
import com.example.exportflow.order.infrastructure.OrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExportTaskCreationService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TASK_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final ExportTaskMapper taskMapper;
    private final ExportRunMapper runMapper;
    private final OutboxMapper outboxMapper;
    private final OrderMapper orderMapper;
    private final RequestHasher requestHasher;
    private final ObjectMapper objectMapper;
    private final ExportProperties properties;

    public ExportTaskCreationService(ExportTaskMapper taskMapper, ExportRunMapper runMapper,
                                     OutboxMapper outboxMapper, OrderMapper orderMapper,
                                     RequestHasher requestHasher, ObjectMapper objectMapper, ExportProperties properties) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.outboxMapper = outboxMapper;
        this.orderMapper = orderMapper;
        this.requestHasher = requestHasher;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public ExportTaskResponse create(CreateExportTaskRequest request, String idempotencyKey) {
        validateKey(idempotencyKey);
        if (request.exportType() == null) throw validation("导出类型不能为空");

        List<Long> selectedIds = normalizeSelected(request);
        FilterSnapshot snapshot = request.exportType() == ExportType.FILTER
                ? FilterSnapshot.from(request.filters() == null ? new OrderFilter() : request.filters()) : null;
        RequestHasher.CanonicalRequest canonical = requestHasher.canonicalize(request.exportType(), selectedIds, snapshot);
        String requestHash = requestHasher.hash(canonical);

        ExportTask existing = taskMapper.findByIdempotencyKey(idempotencyKey);
        if (existing != null) return replayOrConflict(existing, requestHash);

        LocalDateTime now = LocalDateTime.now(ZONE);
        ExportTask task = newTask(idempotencyKey, requestHash, request.exportType(), now);
        if (request.exportType() == ExportType.SELECTED) {
            List<Long> existingIds = orderMapper.findExistingIds(selectedIds);
            if (existingIds.isEmpty()) throw noData();
            task.setExpectedCount(existingIds.size());
            return insertTask(task, existingIds, now);
        } else {
            Long maxId = orderMapper.findMaxId();
            if (maxId == null) throw noData();
            long count;
            try { count = orderMapper.count(snapshot.toFilter(), maxId); }
            catch (RuntimeException exception) { throw CountQuerySupport.map(exception); }
            validateCount(count);
            task.setSnapshotMaxId(maxId);
            task.setFilterSnapshotJson(writeJson(snapshot));
            task.setExpectedCount(count);
            return insertTask(task, List.of(), now);
        }
    }

    private ExportTaskResponse insertTask(ExportTask task, List<Long> items, LocalDateTime now) {
        try {
            taskMapper.insert(task);
            ExportTaskRun initialRun = initialRun(task, now);
            runMapper.insert(initialRun);
            if (taskMapper.setInitialRun(task.getId(), initialRun.getId(), properties.maxManualRetries(), now) != 1) {
                throw new IllegalStateException("Cannot attach initial export run");
            }
            task.setCurrentRunId(initialRun.getId());
            if (!items.isEmpty()) taskMapper.insertItems(task.getId(), items, now);
            String eventId = UUID.randomUUID().toString();
            String payload = writeJson(Map.of("eventId", eventId, "taskId", task.getId(), "eventType", "EXPORT_TASK_CREATED"));
            outboxMapper.insert(eventId, task.getId(), "EXPORT_TASK_CREATED", payload, now, now);
            return ExportTaskResponse.from(task, false);
        } catch (DuplicateKeyException duplicate) {
            ExportTask existing = taskMapper.findByIdempotencyKey(task.getIdempotencyKey());
            if (existing == null) throw duplicate;
            return replayOrConflict(existing, task.getRequestHash());
        }
    }

    private List<Long> normalizeSelected(CreateExportTaskRequest request) {
        if (request.exportType() != ExportType.SELECTED) return List.of();
        List<Long> ids = request.selectedOrderIds() == null ? List.of()
                : request.selectedOrderIds().stream().filter(id -> id != null && id > 0).distinct().sorted().toList();
        if (ids.isEmpty()) throw noData();
        if (ids.size() > properties.maxSelectedRows()) {
            throw new BusinessException("EXPORT_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "已选订单不能超过 " + properties.maxSelectedRows() + " 条");
        }
        return ids;
    }

    private ExportTask newTask(String key, String hash, ExportType type, LocalDateTime now) {
        ExportTask task = new ExportTask();
        task.setTaskNo("EXP" + TASK_TIME.format(now) + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        task.setIdempotencyKey(key);
        task.setRequestHash(hash);
        task.setExportType(type);
        task.setStatus(ExportTaskStatus.PENDING);
        task.setStage(ExportStage.QUEUED);
        task.setSnapshotTime(now);
        task.setExportFieldVersion(1);
        task.setManualRetryLimit(properties.maxManualRetries());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private ExportTaskRun initialRun(ExportTask task, LocalDateTime now) {
        ExportTaskRun run = new ExportTaskRun();
        run.setTaskId(task.getId());
        run.setRunNo(0);
        run.setTriggerType(ExportRunTrigger.INITIAL);
        run.setIdempotencyKey(task.getIdempotencyKey());
        run.setRequestHash(task.getRequestHash());
        run.setStatus(ExportTaskStatus.PENDING);
        run.setStage(ExportStage.QUEUED);
        run.setExpectedCount(task.getExpectedCount());
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return run;
    }

    private ExportTaskResponse replayOrConflict(ExportTask existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT,
                    "相同 Idempotency-Key 不能用于不同请求");
        }
        return ExportTaskResponse.from(existing, true);
    }

    private void validateCount(long count) {
        if (count == 0) throw noData();
        if (count > properties.maxFilterRows()) {
            throw new BusinessException("EXPORT_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "导出数量超过 " + properties.maxFilterRows() + " 条，请缩小筛选范围");
        }
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{8,64}")) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key 请求头缺失或格式错误");
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, message);
    }

    private BusinessException noData() {
        return new BusinessException("NO_EXPORT_DATA", HttpStatus.UNPROCESSABLE_ENTITY, "没有可导出的订单");
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize export task", exception); }
    }

}

package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.common.api.RequestIdContext;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.excel.OrderExcelWriter;
import com.example.exportflow.export.infrastructure.progress.ProgressService;
import com.example.exportflow.export.infrastructure.storage.LocalFileStorage;
import com.example.exportflow.order.infrastructure.OrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ExportExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ExportExecutionService.class);
    private final OrderMapper orderMapper;
    private final ExportTaskMapper taskMapper;
    private final OrderExcelWriter excelWriter;
    private final LocalFileStorage storage;
    private final ProgressService progressService;
    private final TaskFailureService failureService;
    private final TaskSuccessService successService;
    private final ObjectMapper objectMapper;
    private final ExportProperties properties;
    private final ExecutionHeartbeat executionHeartbeat;
    private final Clock clock;

    public ExportExecutionService(OrderMapper orderMapper, ExportTaskMapper taskMapper,
                                  OrderExcelWriter excelWriter, LocalFileStorage storage, ProgressService progressService,
                                  TaskFailureService failureService, TaskSuccessService successService,
                                  ObjectMapper objectMapper, ExportProperties properties,
                                  ExecutionHeartbeat executionHeartbeat, Clock clock) {
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
        this.excelWriter = excelWriter;
        this.storage = storage;
        this.progressService = progressService;
        this.failureService = failureService;
        this.successService = successService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executionHeartbeat = executionHeartbeat;
        this.clock = clock;
    }

    public void execute(ExportTask task) {
        Path temporary = null;
        LocalFileStorage.StoredFile stored = null;
        ExecutionHeartbeat.Session heartbeat = executionHeartbeat.start(task);
        try {
            ensureCurrent(progressService.persist(task, ExportStage.PREPARING, 5, 0));
            temporary = storage.createTemporary(task);
            AtomicLong lastPersistedRows = new AtomicLong(0);
            AtomicReference<LocalDateTime> lastPersistedAt = new AtomicReference<>(LocalDateTime.now(clock));

            OrderExcelWriter.BatchLoader loader = loader(task);
            long written = excelWriter.write(temporary, loader, exported -> {
                int progress = task.getExpectedCount() == 0 ? 5
                        : Math.min(95, 5 + (int) Math.floor((double) exported / task.getExpectedCount() * 90));
                LocalDateTime now = LocalDateTime.now(clock);
                boolean persist = exported - lastPersistedRows.get() >= 10_000
                        || !now.isBefore(lastPersistedAt.get().plus(properties.heartbeatInterval()));
                if (persist) {
                    ensureCurrent(progressService.persist(task, ExportStage.QUERYING_WRITING, progress, exported));
                    lastPersistedRows.set(exported);
                    lastPersistedAt.set(now);
                }
            });

            ensureCurrent(progressService.persist(task, ExportStage.FINALIZING, 95, written));
            if (!Files.exists(temporary) || Files.size(temporary) == 0) {
                throw new IllegalStateException("Excel file is empty");
            }
            ensureCurrent(progressService.persist(task, ExportStage.MOVING, 99, written));
            stored = storage.moveToFinal(task, temporary);
            temporary = null;
            LocalDateTime completedAt = LocalDateTime.now(clock);
            LocalDateTime expireAt = completedAt.plus(properties.fileRetention());
            ensureCurrent(successService.complete(task, stored.downloadName(), stored.relativePath(), stored.size(),
                    written, completedAt, expireAt));
        } catch (SupersededExecution exception) {
            if (stored != null) storage.deleteQuietly(stored.absolutePath());
            log.info("Execution superseded, taskId={}, token={}", task.getId(), task.getExecutionToken());
        } catch (Exception exception) {
            if (stored != null) storage.deleteQuietly(stored.absolutePath());
            log.error("Export failed, requestId={}, taskId={}, token={}", RequestIdContext.currentOrCreate(),
                    task.getId(), task.getExecutionToken(), exception);
            failureService.handle(task, exception);
        } finally {
            heartbeat.close();
            storage.deleteQuietly(temporary);
        }
    }

    private OrderExcelWriter.BatchLoader loader(ExportTask task) throws Exception {
        if (task.getExportType() == ExportType.SELECTED) {
            return afterId -> orderMapper.findSelectedBatch(task.getId(), afterId, properties.queryBatchSize());
        }
        FilterSnapshot snapshot = objectMapper.readValue(task.getFilterSnapshotJson(), FilterSnapshot.class);
        return afterId -> orderMapper.findExportBatch(snapshot.toFilter(), task.getSnapshotMaxId(), afterId,
                properties.queryBatchSize());
    }

    private void ensureCurrent(boolean current) {
        if (!current) throw new SupersededExecution();
    }

    private static final class SupersededExecution extends RuntimeException {
    }
}

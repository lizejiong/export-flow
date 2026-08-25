package com.example.exportflow.export.application;

import com.example.exportflow.common.api.RequestIdContext;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class FileLifecycleService {
    private final ExportTaskMapper taskMapper;
    private final ExportRunMapper runMapper;
    private final ApplicationEventPublisher events;

    public FileLifecycleService(ExportTaskMapper taskMapper, ExportRunMapper runMapper,
                                ApplicationEventPublisher events) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.events = events;
    }

    @Transactional
    public boolean markMissing(ExportTask task, LocalDateTime now) {
        if (taskMapper.markFileMissing(task.getId(), now) != 1) return false;
        requireRun(task, runMapper.markFileMissing(currentRunId(task), now), "missing");
        events.publishEvent(new TaskChangedEvent(task.getId(), "task.failed", RequestIdContext.currentOrCreate()));
        return true;
    }

    @Transactional
    public boolean markExpired(ExportTask task, LocalDateTime now) {
        if (taskMapper.markExpired(task.getId(), now) != 1) return false;
        requireRun(task, runMapper.markExpired(currentRunId(task), now), "expired");
        events.publishEvent(new TaskChangedEvent(task.getId(), "task.expired", RequestIdContext.currentOrCreate()));
        return true;
    }

    @Transactional
    public void recordDownload(ExportTask task, LocalDateTime now) {
        if (taskMapper.incrementDownload(task.getId(), now) != 1) {
            throw new IllegalStateException("Cannot record task download");
        }
        requireRun(task, runMapper.incrementDownload(currentRunId(task), now), "download");
    }

    private long currentRunId(ExportTask task) {
        if (task.getCurrentRunId() == null) throw new IllegalStateException("Export task has no current run");
        return task.getCurrentRunId();
    }

    private void requireRun(ExportTask task, int updated, String operation) {
        if (updated != 1) {
            throw new IllegalStateException("Cannot mark current export run " + operation + ", taskId=" + task.getId());
        }
    }
}

package com.example.exportflow.export.infrastructure.progress;

import com.example.exportflow.common.api.RequestIdContext;
import com.example.exportflow.export.application.TaskChangedEvent;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ProgressService {
    public static final String CHANNEL = "export:task:events";
    private final ExportTaskMapper taskMapper;
    private final ExportAttemptMapper attemptMapper;
    private final ExportRunMapper runMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProgressService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper, ExportRunMapper runMapper,
                           ApplicationEventPublisher events, Clock clock) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public boolean persist(ExportTask task, ExportStage stage, int progress, long exportedCount) {
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = taskMapper.updateProgress(task.getId(), task.getExecutionToken(), stage.name(), progress, exportedCount, now);
        if (updated == 1) {
            if (task.getCurrentRunId() == null || runMapper.updateProgress(task.getCurrentRunId(), task.getExecutionToken(),
                    stage.name(), progress, exportedCount, now) != 1) {
                throw new IllegalStateException("Cannot update current export run progress");
            }
            if (attemptMapper.heartbeat(task.getExecutionToken(), now) != 1) {
                throw new IllegalStateException("Cannot update current export attempt progress");
            }
            events.publishEvent(new TaskChangedEvent(task.getId(), "task.progress", RequestIdContext.currentOrCreate()));
        }
        return updated == 1;
    }

}

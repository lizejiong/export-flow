package com.example.exportflow.export.application;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TaskHeartbeatService {
    private final ExportTaskMapper taskMapper;
    private final ExportRunMapper runMapper;
    private final ExportAttemptMapper attemptMapper;
    private final Clock clock;

    public TaskHeartbeatService(ExportTaskMapper taskMapper, ExportRunMapper runMapper,
                                ExportAttemptMapper attemptMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.attemptMapper = attemptMapper;
        this.clock = clock;
    }

    @Transactional
    public boolean heartbeat(ExportTask task) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (taskMapper.heartbeat(task.getId(), task.getExecutionToken(), now) != 1) return false;
        if (task.getCurrentRunId() == null
                || runMapper.heartbeat(task.getCurrentRunId(), task.getExecutionToken(), now) != 1) {
            throw new IllegalStateException("Cannot heartbeat current export run");
        }
        if (attemptMapper.heartbeat(task.getExecutionToken(), now) != 1) {
            throw new IllegalStateException("Cannot heartbeat current export attempt");
        }
        return true;
    }
}

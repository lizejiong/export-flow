package com.example.exportflow.export.application;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TaskSuccessService {
    private final ExportTaskMapper taskMapper;
    private final ExportRunMapper runMapper;
    private final ExportAttemptMapper attemptMapper;

    public TaskSuccessService(ExportTaskMapper taskMapper, ExportRunMapper runMapper, ExportAttemptMapper attemptMapper) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.attemptMapper = attemptMapper;
    }

    @Transactional
    public boolean complete(ExportTask task, String fileName, String filePath, long fileSize, long exportedCount,
                            LocalDateTime completedAt, LocalDateTime expireAt) {
        int updated = taskMapper.markSuccess(task.getId(), task.getExecutionToken(), fileName, filePath,
                fileSize, exportedCount, completedAt, expireAt);
        if (updated != 1) return false;
        if (task.getCurrentRunId() == null || runMapper.markSuccess(task.getCurrentRunId(), task.getExecutionToken(),
                fileName, filePath, fileSize, exportedCount, completedAt, expireAt) != 1) {
            throw new IllegalStateException("Cannot complete current export run");
        }
        attemptMapper.markSuccess(task.getExecutionToken(), completedAt);
        return true;
    }
}

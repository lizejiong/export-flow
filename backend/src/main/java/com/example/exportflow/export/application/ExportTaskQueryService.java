package com.example.exportflow.export.application;

import com.example.exportflow.common.api.PageResponse;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.web.dto.TaskDetailResponse;
import com.example.exportflow.export.web.dto.TaskSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExportTaskQueryService {
    private final ExportTaskMapper taskMapper;
    private final ExportAttemptMapper attemptMapper;
    private final ExportRunMapper runMapper;

    public ExportTaskQueryService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper, ExportRunMapper runMapper) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
    }

    public PageResponse<TaskSummaryResponse> findPage(String taskNo, ExportType exportType, ExportTaskStatus status,
                                                      LocalDateTime createdFrom, LocalDateTime createdTo,
                                                      int page, int pageSize) {
        validatePage(page, pageSize);
        String normalizedTaskNo = taskNo == null || taskNo.isBlank() ? null : taskNo.trim();
        long total = taskMapper.countTasks(normalizedTaskNo, exportType, status, createdFrom, createdTo);
        List<TaskSummaryResponse> items = taskMapper.findPageTasks(normalizedTaskNo, exportType, status,
                createdFrom, createdTo, ((long) page - 1) * pageSize, pageSize).stream()
                .map(TaskSummaryResponse::from).toList();
        return new PageResponse<>(items, page, pageSize, total);
    }

    public TaskDetailResponse detail(long taskId) {
        ExportTask task = requireTask(taskId);
        long selectedCount = task.getExportType() == ExportType.SELECTED ? taskMapper.countItems(taskId) : 0;
        List<TaskDetailResponse.RunResponse> runs = runMapper.findByTaskId(taskId).stream()
                .map(run -> TaskDetailResponse.RunResponse.from(run, attemptMapper.findByRunId(run.getId())))
                .toList();
        return TaskDetailResponse.from(task, selectedCount, runs);
    }

    public ExportTask requireTask(long taskId) {
        ExportTask task = taskMapper.findById(taskId);
        if (task == null || task.isArchived()) throw new BusinessException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "导出任务不存在");
        return task;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || page > 10_000 || (pageSize != 20 && pageSize != 50 && pageSize != 100)) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "页码必须在 1 到 10000 之间，分页大小只能是 20、50 或 100");
        }
    }
}

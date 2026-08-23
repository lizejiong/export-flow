package com.example.exportflow.export.application;

import com.example.exportflow.common.api.PageResponse;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
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

    public ExportTaskQueryService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
    }

    public PageResponse<TaskSummaryResponse> findPage(String taskNo, ExportType exportType, ExportTaskStatus status,
                                                      LocalDateTime createdFrom, LocalDateTime createdTo,
                                                      int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = switch (pageSize) { case 20, 50, 100 -> pageSize; default -> 20; };
        String normalizedTaskNo = taskNo == null || taskNo.isBlank() ? null : taskNo.trim();
        long total = taskMapper.countTasks(normalizedTaskNo, exportType, status, createdFrom, createdTo);
        List<TaskSummaryResponse> items = taskMapper.findPageTasks(normalizedTaskNo, exportType, status,
                createdFrom, createdTo, (safePage - 1) * safeSize, safeSize).stream()
                .map(TaskSummaryResponse::from).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    public TaskDetailResponse detail(long taskId) {
        ExportTask task = requireTask(taskId);
        long selectedCount = task.getExportType() == ExportType.SELECTED ? taskMapper.countItems(taskId) : 0;
        long root = task.getRootTaskId() == null ? task.getId() : task.getRootTaskId();
        return TaskDetailResponse.from(task, selectedCount, attemptMapper.findByTaskId(taskId), taskMapper.findRetryChain(root));
    }

    public ExportTask requireTask(long taskId) {
        ExportTask task = taskMapper.findById(taskId);
        if (task == null) throw new BusinessException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "导出任务不存在");
        return task;
    }
}


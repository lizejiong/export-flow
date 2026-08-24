package com.example.exportflow.export.application;

import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.storage.LocalFileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class DownloadService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ExportTaskQueryService queryService;
    private final ExportTaskMapper taskMapper;
    private final ExportRunMapper runMapper;
    private final LocalFileStorage storage;

    public DownloadService(ExportTaskQueryService queryService, ExportTaskMapper taskMapper,
                           ExportRunMapper runMapper, LocalFileStorage storage) {
        this.queryService = queryService;
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.storage = storage;
    }

    @Transactional
    public DownloadFile get(long taskId) {
        ExportTask task = queryService.requireTask(taskId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (task.getStatus() == ExportTaskStatus.EXPIRED || task.getFileExpireAt() == null || !task.getFileExpireAt().isAfter(now)) {
            throw new BusinessException("FILE_EXPIRED", HttpStatus.GONE, "导出文件已过期");
        }
        if (task.getStatus() != ExportTaskStatus.SUCCESS || task.getFilePath() == null) {
            throw new BusinessException("TASK_NOT_DOWNLOADABLE", HttpStatus.CONFLICT, "当前任务不可下载");
        }
        Path path = storage.resolveStored(task.getFilePath());
        if (!Files.isRegularFile(path)) {
            taskMapper.markFileMissing(taskId, now);
            if (task.getCurrentRunId() != null) runMapper.markFileMissing(task.getCurrentRunId(), now);
            throw new BusinessException("FILE_MISSING", HttpStatus.NOT_FOUND, "导出文件不存在");
        }
        taskMapper.incrementDownload(taskId, now);
        if (task.getCurrentRunId() != null) runMapper.incrementDownload(task.getCurrentRunId(), now);
        return new DownloadFile(path, task.getFileName(), task.getFileSize() == null ? size(path) : task.getFileSize());
    }

    private long size(Path path) {
        try { return Files.size(path); }
        catch (Exception exception) { return 0; }
    }

    public record DownloadFile(Path path, String fileName, long size) {}
}

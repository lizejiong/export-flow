package com.example.exportflow.export.application;

import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.infrastructure.storage.LocalFileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class DownloadService {
    private final ExportTaskQueryService queryService;
    private final LocalFileStorage storage;
    private final FileLifecycleService lifecycleService;
    private final Clock clock;

    public DownloadService(ExportTaskQueryService queryService, LocalFileStorage storage,
                           FileLifecycleService lifecycleService, Clock clock) {
        this.queryService = queryService;
        this.storage = storage;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    public DownloadFile get(long taskId) {
        ExportTask task = queryService.requireTask(taskId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (task.getStatus() == ExportTaskStatus.EXPIRED || task.getFileExpireAt() == null
                || !task.getFileExpireAt().isAfter(now)) {
            throw new BusinessException("FILE_EXPIRED", HttpStatus.GONE, "导出文件已过期");
        }
        if (task.getStatus() != ExportTaskStatus.SUCCESS || task.getFilePath() == null) {
            throw new BusinessException("TASK_NOT_DOWNLOADABLE", HttpStatus.CONFLICT, "当前任务不可下载");
        }
        Path path = storage.resolveStored(task.getFilePath());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            lifecycleService.markMissing(task, now);
            throw new BusinessException("FILE_MISSING", HttpStatus.NOT_FOUND, "导出文件不存在");
        }
        lifecycleService.recordDownload(task, now);
        return new DownloadFile(path, task.getFileName(), task.getFileSize() == null ? size(path) : task.getFileSize());
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (Exception exception) {
            return 0;
        }
    }

    public record DownloadFile(Path path, String fileName, long size) {
    }
}

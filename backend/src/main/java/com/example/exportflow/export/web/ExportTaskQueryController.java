package com.example.exportflow.export.web;

import com.example.exportflow.common.api.PageResponse;
import com.example.exportflow.export.application.DownloadService;
import com.example.exportflow.export.application.ExportTaskQueryService;
import com.example.exportflow.export.application.RetryService;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.export.web.dto.ExportTaskResponse;
import com.example.exportflow.export.web.dto.TaskDetailResponse;
import com.example.exportflow.export.web.dto.TaskSummaryResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/export-tasks")
public class ExportTaskQueryController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ExportTaskQueryService queryService;
    private final DownloadService downloadService;
    private final RetryService retryService;

    public ExportTaskQueryController(ExportTaskQueryService queryService, DownloadService downloadService, RetryService retryService) {
        this.queryService = queryService;
        this.downloadService = downloadService;
        this.retryService = retryService;
    }

    @GetMapping
    public PageResponse<TaskSummaryResponse> findPage(
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) ExportType exportType,
            @RequestParam(required = false) ExportTaskStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return queryService.findPage(taskNo, exportType, status, createdFrom, createdTo, page, pageSize);
    }

    @GetMapping("/{taskId}")
    public TaskDetailResponse detail(@PathVariable long taskId) {
        return queryService.detail(taskId);
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<ExportTaskResponse> retry(
            @PathVariable long taskId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ExportTaskResponse response = retryService.retry(taskId, idempotencyKey);
        return response.idempotentReplay() ? ResponseEntity.ok(response) : ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{taskId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long taskId) throws IOException {
        DownloadService.DownloadFile file = downloadService.get(taskId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(Files.newInputStream(file.path())));
    }
}


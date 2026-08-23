package com.example.exportflow.export.web;

import com.example.exportflow.export.application.ExportTaskCreationService;
import com.example.exportflow.export.web.dto.CreateExportTaskRequest;
import com.example.exportflow.export.web.dto.ExportTaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/export-tasks")
public class ExportTaskController {
    private final ExportTaskCreationService creationService;

    public ExportTaskController(ExportTaskCreationService creationService) {
        this.creationService = creationService;
    }

    @PostMapping
    public ResponseEntity<ExportTaskResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateExportTaskRequest request
    ) {
        ExportTaskResponse response = creationService.create(request, idempotencyKey);
        if (response.idempotentReplay()) return ResponseEntity.ok(response);
        return ResponseEntity.created(URI.create("/api/export-tasks/" + response.id())).body(response);
    }
}

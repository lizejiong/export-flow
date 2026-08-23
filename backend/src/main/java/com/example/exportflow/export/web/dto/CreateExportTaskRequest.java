package com.example.exportflow.export.web.dto;

import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.order.application.OrderFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateExportTaskRequest(
        @NotNull ExportType exportType,
        List<@Positive Long> selectedOrderIds,
        @Valid OrderFilter filters
) {
}


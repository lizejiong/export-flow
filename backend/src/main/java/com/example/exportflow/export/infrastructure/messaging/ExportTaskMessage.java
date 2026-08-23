package com.example.exportflow.export.infrastructure.messaging;

public record ExportTaskMessage(String eventId, long taskId, String eventType) {
}


package com.example.exportflow.export.infrastructure.messaging;

public record ExportTaskMessage(
        Integer schemaVersion,
        String eventId,
        long taskId,
        String eventType,
        String requestId
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static ExportTaskMessage current(String eventId, long taskId, String eventType, String requestId) {
        return new ExportTaskMessage(CURRENT_SCHEMA_VERSION, eventId, taskId, eventType, requestId);
    }

    public boolean isSupported() {
        return (schemaVersion == null || schemaVersion == CURRENT_SCHEMA_VERSION)
                && eventId != null && !eventId.isBlank() && taskId > 0
                && eventType != null && !eventType.isBlank();
    }
}

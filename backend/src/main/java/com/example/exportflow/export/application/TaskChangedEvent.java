package com.example.exportflow.export.application;

public record TaskChangedEvent(long taskId, String eventType, String requestId) {
}

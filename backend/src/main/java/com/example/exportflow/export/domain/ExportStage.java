package com.example.exportflow.export.domain;

public enum ExportStage {
    QUEUED, RETRY_WAITING, PREPARING, QUERYING_WRITING, FINALIZING, MOVING, RECOVERING, COMPLETED
}


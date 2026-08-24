package com.example.exportflow.export.domain;

import java.time.LocalDateTime;

public class ExportTaskRun {
    private Long id;
    private long taskId;
    private int runNo;
    private ExportRunTrigger triggerType;
    private String idempotencyKey;
    private String requestHash;
    private ExportTaskStatus status;
    private ExportStage stage;
    private long expectedCount;
    private long exportedCount;
    private int progress;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private LocalDateTime fileExpireAt;
    private int autoAttemptCount;
    private String failureCode;
    private String failureMessage;
    private boolean retryable;
    private String workerId;
    private String executionToken;
    private LocalDateTime heartbeatAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastDownloadedAt;
    private int downloadCount;
    private Long legacyTaskId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public int getRunNo() { return runNo; }
    public void setRunNo(int runNo) { this.runNo = runNo; }
    public ExportRunTrigger getTriggerType() { return triggerType; }
    public void setTriggerType(ExportRunTrigger triggerType) { this.triggerType = triggerType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public ExportTaskStatus getStatus() { return status; }
    public void setStatus(ExportTaskStatus status) { this.status = status; }
    public ExportStage getStage() { return stage; }
    public void setStage(ExportStage stage) { this.stage = stage; }
    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }
    public long getExportedCount() { return exportedCount; }
    public void setExportedCount(long exportedCount) { this.exportedCount = exportedCount; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public LocalDateTime getFileExpireAt() { return fileExpireAt; }
    public void setFileExpireAt(LocalDateTime fileExpireAt) { this.fileExpireAt = fileExpireAt; }
    public int getAutoAttemptCount() { return autoAttemptCount; }
    public void setAutoAttemptCount(int autoAttemptCount) { this.autoAttemptCount = autoAttemptCount; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public String getExecutionToken() { return executionToken; }
    public void setExecutionToken(String executionToken) { this.executionToken = executionToken; }
    public LocalDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(LocalDateTime heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getLastDownloadedAt() { return lastDownloadedAt; }
    public void setLastDownloadedAt(LocalDateTime lastDownloadedAt) { this.lastDownloadedAt = lastDownloadedAt; }
    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }
    public Long getLegacyTaskId() { return legacyTaskId; }
    public void setLegacyTaskId(Long legacyTaskId) { this.legacyTaskId = legacyTaskId; }
}

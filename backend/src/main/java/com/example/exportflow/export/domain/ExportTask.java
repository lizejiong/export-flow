package com.example.exportflow.export.domain;

import java.time.LocalDateTime;

public class ExportTask {
    private Long id;
    private String taskNo;
    private String idempotencyKey;
    private String requestHash;
    private ExportType exportType;
    private ExportTaskStatus status;
    private ExportStage stage;
    private String filterSnapshotJson;
    private Long snapshotMaxId;
    private LocalDateTime snapshotTime;
    private int exportFieldVersion;
    private long expectedCount;
    private long exportedCount;
    private int progress;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private LocalDateTime fileExpireAt;
    private int autoAttemptCount;
    private int manualRetryIndex;
    private Long sourceTaskId;
    private Long rootTaskId;
    private String failureCode;
    private String failureMessage;
    private boolean retryable;
    private String workerId;
    private String executionToken;
    private LocalDateTime heartbeatAt;
    private long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastDownloadedAt;
    private int downloadCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public ExportType getExportType() { return exportType; }
    public void setExportType(ExportType exportType) { this.exportType = exportType; }
    public ExportTaskStatus getStatus() { return status; }
    public void setStatus(ExportTaskStatus status) { this.status = status; }
    public ExportStage getStage() { return stage; }
    public void setStage(ExportStage stage) { this.stage = stage; }
    public String getFilterSnapshotJson() { return filterSnapshotJson; }
    public void setFilterSnapshotJson(String filterSnapshotJson) { this.filterSnapshotJson = filterSnapshotJson; }
    public Long getSnapshotMaxId() { return snapshotMaxId; }
    public void setSnapshotMaxId(Long snapshotMaxId) { this.snapshotMaxId = snapshotMaxId; }
    public LocalDateTime getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(LocalDateTime snapshotTime) { this.snapshotTime = snapshotTime; }
    public int getExportFieldVersion() { return exportFieldVersion; }
    public void setExportFieldVersion(int exportFieldVersion) { this.exportFieldVersion = exportFieldVersion; }
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
    public int getManualRetryIndex() { return manualRetryIndex; }
    public void setManualRetryIndex(int manualRetryIndex) { this.manualRetryIndex = manualRetryIndex; }
    public Long getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(Long sourceTaskId) { this.sourceTaskId = sourceTaskId; }
    public Long getRootTaskId() { return rootTaskId; }
    public void setRootTaskId(Long rootTaskId) { this.rootTaskId = rootTaskId; }
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
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
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
}


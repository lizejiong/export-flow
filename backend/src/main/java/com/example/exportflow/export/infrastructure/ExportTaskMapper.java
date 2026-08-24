package com.example.exportflow.export.infrastructure;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExportTaskMapper {
    ExportTask findById(@Param("id") long id);
    ExportTask findByIdForUpdate(@Param("id") long id);
    ExportTask findByIdempotencyKey(@Param("key") String key);
    int insert(ExportTask task);
    int setInitialRun(@Param("taskId") long taskId, @Param("runId") long runId,
                      @Param("manualRetryLimit") int manualRetryLimit, @Param("now") LocalDateTime now);
    int startManualRun(@Param("taskId") long taskId, @Param("expectedRunId") long expectedRunId,
                       @Param("runId") long runId, @Param("runNo") int runNo,
                       @Param("now") LocalDateTime now);
    int insertItems(@Param("taskId") long taskId, @Param("orderIds") List<Long> orderIds,
                    @Param("createdAt") LocalDateTime createdAt);
    List<Long> findItemIds(@Param("taskId") long taskId);
    int claim(@Param("taskId") long taskId, @Param("workerId") String workerId,
              @Param("executionToken") String executionToken, @Param("now") LocalDateTime now,
              @Param("maxAttempts") int maxAttempts);
    int updateProgress(@Param("taskId") long taskId, @Param("executionToken") String executionToken,
                       @Param("stage") String stage, @Param("progress") int progress,
                       @Param("exportedCount") long exportedCount, @Param("now") LocalDateTime now);
    int heartbeat(@Param("taskId") long taskId, @Param("executionToken") String executionToken,
                  @Param("now") LocalDateTime now);
    int markSuccess(@Param("taskId") long taskId, @Param("executionToken") String executionToken,
                    @Param("fileName") String fileName, @Param("filePath") String filePath,
                    @Param("fileSize") long fileSize, @Param("exportedCount") long exportedCount,
                    @Param("completedAt") LocalDateTime completedAt, @Param("expireAt") LocalDateTime expireAt);
    int markRetryPending(@Param("taskId") long taskId, @Param("executionToken") String executionToken,
                         @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage,
                         @Param("now") LocalDateTime now);
    int markFailed(@Param("taskId") long taskId, @Param("executionToken") String executionToken,
                   @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage,
                   @Param("retryable") boolean retryable, @Param("now") LocalDateTime now);
    List<ExportTask> findStale(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
    int recoverStale(@Param("taskId") long taskId, @Param("executionToken") String executionToken,
                     @Param("observedHeartbeatAt") LocalDateTime observedHeartbeatAt, @Param("now") LocalDateTime now);
    int failStale(@Param("taskId") long taskId, @Param("executionToken") String executionToken,
                  @Param("observedHeartbeatAt") LocalDateTime observedHeartbeatAt, @Param("now") LocalDateTime now);
    int copyItems(@Param("sourceTaskId") long sourceTaskId, @Param("targetTaskId") long targetTaskId,
                  @Param("createdAt") LocalDateTime createdAt);
    long countItems(@Param("taskId") long taskId);
    List<ExportTask> findPageTasks(@Param("taskNo") String taskNo, @Param("exportType") ExportType exportType,
                                   @Param("status") ExportTaskStatus status, @Param("createdFrom") LocalDateTime createdFrom,
                                   @Param("createdTo") LocalDateTime createdTo, @Param("offset") int offset,
                                   @Param("limit") int limit);
    long countTasks(@Param("taskNo") String taskNo, @Param("exportType") ExportType exportType,
                    @Param("status") ExportTaskStatus status, @Param("createdFrom") LocalDateTime createdFrom,
                    @Param("createdTo") LocalDateTime createdTo);
    List<ExportTask> findRetryChain(@Param("rootTaskId") long rootTaskId);
    int incrementDownload(@Param("taskId") long taskId, @Param("now") LocalDateTime now);
    int markFileMissing(@Param("taskId") long taskId, @Param("now") LocalDateTime now);
    List<ExportTask> findExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int markExpired(@Param("taskId") long taskId, @Param("now") LocalDateTime now);
}

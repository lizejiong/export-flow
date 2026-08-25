package com.example.exportflow.export.infrastructure;

import com.example.exportflow.export.domain.ExportTaskRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExportRunMapper {
    ExportTaskRun findById(@Param("id") long id);
    ExportTaskRun findByIdempotencyKey(@Param("key") String key);
    ExportTaskRun findByIdempotencyKeyForUpdate(@Param("key") String key);
    List<ExportTaskRun> findByTaskId(@Param("taskId") long taskId);
    int insert(ExportTaskRun run);
    int markProcessing(@Param("runId") long runId, @Param("workerId") String workerId,
                       @Param("executionToken") String executionToken, @Param("now") LocalDateTime now,
                       @Param("maxAttempts") int maxAttempts);
    int updateProgress(@Param("runId") long runId, @Param("executionToken") String executionToken,
                       @Param("stage") String stage, @Param("progress") int progress,
                       @Param("exportedCount") long exportedCount, @Param("now") LocalDateTime now);
    int heartbeat(@Param("runId") long runId, @Param("executionToken") String executionToken,
                  @Param("now") LocalDateTime now);
    int markSuccess(@Param("runId") long runId, @Param("executionToken") String executionToken,
                    @Param("fileName") String fileName, @Param("filePath") String filePath,
                    @Param("fileSize") long fileSize, @Param("exportedCount") long exportedCount,
                    @Param("completedAt") LocalDateTime completedAt, @Param("expireAt") LocalDateTime expireAt);
    int markRetryPending(@Param("runId") long runId, @Param("executionToken") String executionToken,
                         @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage,
                         @Param("now") LocalDateTime now);
    int markFailed(@Param("runId") long runId, @Param("executionToken") String executionToken,
                   @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage,
                   @Param("retryable") boolean retryable, @Param("now") LocalDateTime now);
    int recoverStale(@Param("runId") long runId, @Param("executionToken") String executionToken,
                     @Param("observedHeartbeatAt") LocalDateTime observedHeartbeatAt, @Param("now") LocalDateTime now);
    int failStale(@Param("runId") long runId, @Param("executionToken") String executionToken,
                  @Param("observedHeartbeatAt") LocalDateTime observedHeartbeatAt, @Param("now") LocalDateTime now);
    int incrementDownload(@Param("runId") long runId, @Param("now") LocalDateTime now);
    int markFileMissing(@Param("runId") long runId, @Param("now") LocalDateTime now);
    int markExpired(@Param("runId") long runId, @Param("now") LocalDateTime now);
}

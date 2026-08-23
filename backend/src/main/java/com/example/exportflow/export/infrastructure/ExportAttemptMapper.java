package com.example.exportflow.export.infrastructure;

import com.example.exportflow.export.domain.ExportTaskAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExportAttemptMapper {
    int insert(ExportTaskAttempt attempt);
    int heartbeat(@Param("executionToken") String executionToken, @Param("now") LocalDateTime now);
    int markSuccess(@Param("executionToken") String executionToken, @Param("now") LocalDateTime now);
    int markFailed(@Param("executionToken") String executionToken, @Param("status") String status,
                   @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage,
                   @Param("now") LocalDateTime now);
    List<ExportTaskAttempt> findByTaskId(@Param("taskId") long taskId);
}


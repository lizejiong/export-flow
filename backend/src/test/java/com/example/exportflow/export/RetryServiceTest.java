package com.example.exportflow.export;

import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.application.RetryService;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskRun;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryServiceTest {
    private ExportTaskMapper taskMapper;
    private ExportRunMapper runMapper;
    private OutboxMapper outboxMapper;
    private RetryService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(ExportTaskMapper.class);
        runMapper = mock(ExportRunMapper.class);
        outboxMapper = mock(OutboxMapper.class);
        service = new RetryService(taskMapper, runMapper, outboxMapper, new ObjectMapper());
    }

    @Test
    void createsNextRunOnTheSameLogicalTask() {
        ExportTask task = failedTask(0, 2);
        when(taskMapper.findByIdForUpdate(11L)).thenReturn(task);
        when(runMapper.insert(any())).thenAnswer(invocation -> {
            ExportTaskRun run = invocation.getArgument(0);
            run.setId(101L);
            return 1;
        });
        when(taskMapper.startManualRun(anyLong(), anyLong(), anyLong(), anyInt(), any())).thenReturn(1);

        var response = service.retry(11L, "retry-key-0001");

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.currentRunNo()).isEqualTo(1);
        assertThat(response.idempotentReplay()).isFalse();
        ArgumentCaptor<ExportTaskRun> runCaptor = ArgumentCaptor.forClass(ExportTaskRun.class);
        verify(runMapper).insert(runCaptor.capture());
        assertThat(runCaptor.getValue().getTaskId()).isEqualTo(11L);
        assertThat(runCaptor.getValue().getRunNo()).isEqualTo(1);
        verify(outboxMapper).insert(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void rejectsRetryAfterTheTaskLimitIsReached() {
        when(taskMapper.findByIdForUpdate(11L)).thenReturn(failedTask(2, 2));

        assertThatThrownBy(() -> service.retry(11L, "retry-key-0002"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("MANUAL_RETRY_LIMIT_REACHED"));
    }

    @Test
    void returnsTheSameLogicalTaskForAnIdempotentReplay() {
        ExportTaskRun existing = new ExportTaskRun();
        existing.setTaskId(11L);
        existing.setRequestHash(retryHash(11L));
        when(runMapper.findByIdempotencyKey("retry-key-0003")).thenReturn(existing);
        when(taskMapper.findById(11L)).thenReturn(failedTask(1, 2));

        var response = service.retry(11L, "retry-key-0003");

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.idempotentReplay()).isTrue();
    }

    @Test
    void rechecksTheIdempotencyKeyAfterAcquiringTheTaskLock() {
        ExportTask source = failedTask(0, 2);
        ExportTaskRun winner = new ExportTaskRun();
        winner.setTaskId(11L);
        winner.setRequestHash(retryHash(11L));
        when(taskMapper.findByIdForUpdate(11L)).thenReturn(source);
        when(runMapper.findByIdempotencyKeyForUpdate("retry-key-0004")).thenReturn(winner);
        when(taskMapper.findById(11L)).thenReturn(source);

        var response = service.retry(11L, "retry-key-0004");

        assertThat(response.idempotentReplay()).isTrue();
        verify(runMapper, org.mockito.Mockito.never()).insert(any());
    }

    private ExportTask failedTask(int retryCount, int retryLimit) {
        ExportTask task = new ExportTask();
        task.setId(11L);
        task.setTaskNo("EXP-11");
        task.setExportType(ExportType.FILTER);
        task.setStatus(ExportTaskStatus.FAILED);
        task.setStage(ExportStage.COMPLETED);
        task.setRetryable(true);
        task.setCurrentRunId(100L + retryCount);
        task.setCurrentRunNo(retryCount);
        task.setManualRetryCount(retryCount);
        task.setManualRetryLimit(retryLimit);
        task.setExpectedCount(20);
        return task;
    }

    private String retryHash(long taskId) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("retry:" + taskId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

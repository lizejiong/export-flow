package com.example.exportflow.export;

import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.application.ExportFailureClassifier;
import com.example.exportflow.export.application.FileLifecycleService;
import com.example.exportflow.export.application.TaskChangedEvent;
import com.example.exportflow.export.application.TaskHeartbeatService;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.messaging.ExportTaskMessage;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreReliabilityServicesTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void failureClassifierNeverExposesInfrastructureDetails() {
        ExportFailureClassifier classifier = new ExportFailureClassifier();

        var failure = classifier.classify(new IllegalStateException("select password from secret_table"));

        assertThat(failure.code()).isEqualTo("EXPORT_SYSTEM_ERROR");
        assertThat(failure.message()).doesNotContain("password", "secret_table");
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void businessErrorsKeepTheirExplicitClientSafeMessage() {
        ExportFailureClassifier classifier = new ExportFailureClassifier();

        var failure = classifier.classify(new BusinessException("LIMIT", HttpStatus.UNPROCESSABLE_ENTITY, "导出数量超限"));

        assertThat(failure.message()).isEqualTo("导出数量超限");
        assertThat(failure.retryable()).isFalse();
    }

    @Test
    void heartbeatStopsWithoutTouchingRunOrAttemptWhenTaskFenceIsLost() {
        ExportTaskMapper tasks = mock(ExportTaskMapper.class);
        ExportRunMapper runs = mock(ExportRunMapper.class);
        ExportAttemptMapper attempts = mock(ExportAttemptMapper.class);
        ExportTask task = processingTask();
        when(tasks.heartbeat(anyLong(), any(), any())).thenReturn(0);

        boolean current = new TaskHeartbeatService(tasks, runs, attempts, CLOCK).heartbeat(task);

        assertThat(current).isFalse();
        verify(runs, never()).heartbeat(anyLong(), any(), any());
        verify(attempts, never()).heartbeat(any(), any());
    }

    @Test
    void heartbeatRejectsATaskRunSplitBrain() {
        ExportTaskMapper tasks = mock(ExportTaskMapper.class);
        ExportRunMapper runs = mock(ExportRunMapper.class);
        ExportAttemptMapper attempts = mock(ExportAttemptMapper.class);
        ExportTask task = processingTask();
        when(tasks.heartbeat(anyLong(), any(), any())).thenReturn(1);
        when(runs.heartbeat(anyLong(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> new TaskHeartbeatService(tasks, runs, attempts, CLOCK).heartbeat(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current export run");
        verify(attempts, never()).heartbeat(any(), any());
    }

    @Test
    void fileMissingPublishesOnlyAfterBothRowsWereUpdated() {
        ExportTaskMapper tasks = mock(ExportTaskMapper.class);
        ExportRunMapper runs = mock(ExportRunMapper.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ExportTask task = processingTask();
        LocalDateTime now = LocalDateTime.now(CLOCK);
        when(tasks.markFileMissing(task.getId(), now)).thenReturn(1);
        when(runs.markFileMissing(task.getCurrentRunId(), now)).thenReturn(1);

        boolean updated = new FileLifecycleService(tasks, runs, events).markMissing(task, now);

        assertThat(updated).isTrue();
        verify(events).publishEvent(any(TaskChangedEvent.class));
    }

    @Test
    void messageEnvelopeAcceptsLegacyAndRejectsUnknownSchemas() {
        assertThat(new ExportTaskMessage(null, "event", 1, "EXPORT_TASK_CREATED", null).isSupported()).isTrue();
        assertThat(new ExportTaskMessage(99, "event", 1, "EXPORT_TASK_CREATED", "request").isSupported()).isFalse();
        assertThat(new ExportTaskMessage(1, null, 1, "EXPORT_TASK_CREATED", "request").isSupported()).isFalse();
    }

    private ExportTask processingTask() {
        ExportTask task = new ExportTask();
        task.setId(11L);
        task.setCurrentRunId(21L);
        task.setExecutionToken("token");
        return task;
    }
}

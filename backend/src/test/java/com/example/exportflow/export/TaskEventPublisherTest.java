package com.example.exportflow.export;

import com.example.exportflow.export.application.TaskChangedEvent;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.progress.ProgressService;
import com.example.exportflow.export.infrastructure.progress.TaskEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskEventPublisherTest {
    @Test
    void reloadsAndPublishesTheCommittedDatabaseVersion() {
        ExportTaskMapper tasks = mock(ExportTaskMapper.class);
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        ExportTask task = task();
        when(tasks.findById(11L)).thenReturn(task);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForHash()).thenReturn(hashes);

        new TaskEventPublisher(tasks, provider, new ObjectMapper().findAndRegisterModules())
                .publish(new TaskChangedEvent(11L, "task.succeeded", "request-123"));

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(ProgressService.CHANNEL), payload.capture());
        assertThat(payload.getValue()).contains("\"version\":7", "\"requestId\":\"request-123\"");
        verify(hashes).putAll(eq("export:task:11:progress"), any());
    }

    private ExportTask task() {
        ExportTask task = new ExportTask();
        task.setId(11L);
        task.setVersion(7);
        task.setStatus(ExportTaskStatus.SUCCESS);
        task.setStage(ExportStage.COMPLETED);
        task.setProgress(100);
        task.setExpectedCount(10);
        task.setExportedCount(10);
        task.setCurrentRunNo(0);
        task.setUpdatedAt(LocalDateTime.of(2026, 8, 24, 20, 0));
        return task;
    }
}

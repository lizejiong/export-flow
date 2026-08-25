package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.export.domain.ExportTask;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Component
public class ExecutionHeartbeat {
    private static final Logger log = LoggerFactory.getLogger(ExecutionHeartbeat.class);
    private final TaskHeartbeatService heartbeatService;
    private final ExportProperties properties;
    private final ScheduledExecutorService scheduler;

    public ExecutionHeartbeat(TaskHeartbeatService heartbeatService, ExportProperties properties) {
        this.heartbeatService = heartbeatService;
        this.properties = properties;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "export-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newScheduledThreadPool(2, factory);
    }

    public Session start(ExportTask task) {
        long intervalMillis = Math.max(1_000, properties.heartbeatInterval().toMillis());
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!heartbeatService.heartbeat(task)) {
                    log.info("Heartbeat stopped for superseded execution, taskId={}, token={}",
                            task.getId(), task.getExecutionToken());
                }
            } catch (Exception exception) {
                log.error("Export heartbeat failed, taskId={}, token={}",
                        task.getId(), task.getExecutionToken(), exception);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    @FunctionalInterface
    public interface Session extends AutoCloseable {
        @Override
        void close();
    }
}

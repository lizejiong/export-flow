package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@Profile("!test")
public class RecoveryScheduler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ExportTaskMapper taskMapper;
    private final RecoveryService recoveryService;
    private final ExportProperties properties;

    public RecoveryScheduler(ExportTaskMapper taskMapper, RecoveryService recoveryService, ExportProperties properties) {
        this.taskMapper = taskMapper;
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.export.recovery-interval:30s}")
    public void recoverStaleTasks() {
        LocalDateTime cutoff = LocalDateTime.now(ZONE).minus(properties.staleAfter());
        for (ExportTask task : taskMapper.findStale(cutoff, 100)) recoveryService.recover(task);
    }
}

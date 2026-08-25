package com.example.exportflow.export.application;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.export.domain.AttemptStatus;
import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.domain.ExportTaskAttempt;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskClaimService {
    private final ExportTaskMapper taskMapper;
    private final ExportAttemptMapper attemptMapper;
    private final ExportRunMapper runMapper;
    private final ExportProperties properties;
    private final String workerId;
    private final Clock clock;

    public TaskClaimService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper,
                            ExportRunMapper runMapper, ExportProperties properties) {
        this(taskMapper, attemptMapper, runMapper, properties, Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }

    @Autowired
    public TaskClaimService(ExportTaskMapper taskMapper, ExportAttemptMapper attemptMapper,
                            ExportRunMapper runMapper, ExportProperties properties, Clock clock) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
        this.properties = properties;
        this.workerId = buildWorkerId();
        this.clock = clock;
    }

    @Transactional
    public Optional<ExportTask> claim(long taskId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String token = UUID.randomUUID().toString();
        if (taskMapper.claim(taskId, workerId, token, now, properties.maxAutoAttempts()) != 1) {
            return Optional.empty();
        }
        ExportTask task = taskMapper.findById(taskId);
        if (task.getCurrentRunId() == null || runMapper.markProcessing(task.getCurrentRunId(), workerId, token, now,
                properties.maxAutoAttempts()) != 1) {
            throw new IllegalStateException("Cannot claim current export run");
        }
        attemptMapper.insert(new ExportTaskAttempt(null, taskId, task.getCurrentRunId(), task.getAutoAttemptCount(), token, workerId,
                AttemptStatus.PROCESSING, now, now, null, null, null));
        return Optional.of(task);
    }

    private String buildWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception exception) {
            return "worker:" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}

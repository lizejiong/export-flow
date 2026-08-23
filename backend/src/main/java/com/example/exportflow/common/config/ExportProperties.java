package com.example.exportflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.export")
public record ExportProperties(
        long maxFilterRows,
        int maxSelectedRows,
        int queryBatchSize,
        int sxssfRowWindow,
        int countTimeoutSeconds,
        int maxAutoAttempts,
        int maxManualRetries,
        Duration heartbeatInterval,
        Duration staleAfter,
        Duration recoveryInterval,
        Duration retryDelay,
        Duration outboxInterval,
        Duration fileRetention,
        Duration cleanupInterval,
        Path storageRoot,
        long minimumFreeSpaceBytes
) {
}


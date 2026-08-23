package com.example.exportflow.support;

import com.example.exportflow.common.config.ExportProperties;

import java.nio.file.Path;
import java.time.Duration;

public final class TestExportProperties {
    private TestExportProperties() {}

    public static ExportProperties create(Path storageRoot) {
        return new ExportProperties(
                1_000_000, 5_000, 1_000, 500, 5, 3, 2,
                Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(30),
                Duration.ofSeconds(15), Duration.ofSeconds(1), Duration.ofHours(24),
                Duration.ofHours(1), storageRoot, 0
        );
    }
}


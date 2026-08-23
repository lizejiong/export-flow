package com.example.exportflow.export.application;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.storage.LocalFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@Component
@Profile("!test")
public class FileCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ExportTaskMapper taskMapper;
    private final LocalFileStorage storage;

    public FileCleanupScheduler(ExportTaskMapper taskMapper, LocalFileStorage storage) {
        this.taskMapper = taskMapper;
        this.storage = storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { cleanup(); }

    @Scheduled(fixedDelayString = "${app.export.cleanup-interval:1h}")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (ExportTask task : taskMapper.findExpired(now, 100)) {
            try {
                if (task.getFilePath() != null) Files.deleteIfExists(storage.resolveStored(task.getFilePath()));
                taskMapper.markExpired(task.getId(), now);
            } catch (Exception exception) {
                log.warn("Cannot expire task file, taskId={}: {}", task.getId(), exception.getMessage());
            }
        }
        cleanupTemporaryFiles();
    }

    private void cleanupTemporaryFiles() {
        Instant cutoff = Instant.now().minus(2, ChronoUnit.HOURS);
        try (Stream<Path> files = Files.list(storage.temporaryDirectory())) {
            files.filter(Files::isRegularFile).filter(path -> olderThan(path, cutoff)).forEach(storage::deleteQuietly);
        } catch (Exception exception) {
            log.warn("Cannot clean temporary export files: {}", exception.getMessage());
        }
    }

    private boolean olderThan(Path path, Instant cutoff) {
        try { return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff); }
        catch (Exception exception) { return false; }
    }
}

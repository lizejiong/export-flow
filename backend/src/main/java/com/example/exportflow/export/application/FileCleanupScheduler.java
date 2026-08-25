package com.example.exportflow.export.application;

import com.example.exportflow.export.domain.ExportTask;
import com.example.exportflow.export.infrastructure.ExportTaskMapper;
import com.example.exportflow.export.infrastructure.storage.LocalFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.stream.Stream;

@Component
@Profile("!test")
public class FileCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);
    private static final Duration ORPHAN_GRACE = Duration.ofHours(2);
    private final ExportTaskMapper taskMapper;
    private final LocalFileStorage storage;
    private final FileLifecycleService lifecycleService;
    private final Clock clock;

    public FileCleanupScheduler(ExportTaskMapper taskMapper, LocalFileStorage storage,
                                FileLifecycleService lifecycleService, Clock clock) {
        this.taskMapper = taskMapper;
        this.storage = storage;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        cleanup();
    }

    @Scheduled(fixedDelayString = "${app.export.cleanup-interval:1h}")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (ExportTask task : taskMapper.findExpired(now, 100)) {
            try {
                String filePath = task.getFilePath();
                if (lifecycleService.markExpired(task, now) && filePath != null) {
                    storage.deleteQuietly(storage.resolveStored(filePath));
                }
            } catch (Exception exception) {
                log.warn("Cannot expire task file, taskId={}", task.getId(), exception);
            }
        }
        Instant cutoff = clock.instant().minus(ORPHAN_GRACE);
        cleanupTemporaryFiles(cutoff);
        cleanupOrphanFinalFiles(cutoff);
    }

    private void cleanupTemporaryFiles(Instant cutoff) {
        try (Stream<Path> files = Files.list(storage.temporaryDirectory())) {
            files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> olderThan(path, cutoff))
                    .forEach(storage::deleteQuietly);
        } catch (Exception exception) {
            log.warn("Cannot clean temporary export files", exception);
        }
    }

    private void cleanupOrphanFinalFiles(Instant cutoff) {
        try (Stream<Path> files = Files.list(storage.finalDirectory())) {
            files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".xlsx"))
                    .filter(path -> olderThan(path, cutoff))
                    .forEach(this::deleteIfOrphan);
        } catch (Exception exception) {
            log.warn("Cannot reconcile final export files", exception);
        }
    }

    private void deleteIfOrphan(Path path) {
        try {
            String relativePath = storage.relativePath(path);
            if (taskMapper.countFileReferences(relativePath) == 0) storage.deleteQuietly(path);
        } catch (Exception exception) {
            log.warn("Cannot reconcile export file {}", path.getFileName(), exception);
        }
    }

    private boolean olderThan(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);
        } catch (Exception exception) {
            return false;
        }
    }
}

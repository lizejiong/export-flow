package com.example.exportflow.export.infrastructure.storage;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportTask;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class LocalFileStorage {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final ExportProperties properties;
    private Path root;
    private Path temporaryDirectory;
    private Path finalDirectory;

    public LocalFileStorage(ExportProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() throws IOException {
        root = properties.storageRoot().toAbsolutePath().normalize();
        temporaryDirectory = root.resolve("tmp");
        finalDirectory = root.resolve("files");
        Files.createDirectories(temporaryDirectory);
        Files.createDirectories(finalDirectory);
    }

    public Path createTemporary(ExportTask task) throws IOException {
        ensureDiskSpace();
        return Files.createTempFile(temporaryDirectory,
                task.getTaskNo() + "_" + shortToken(task.getExecutionToken()) + "_", ".xlsx.part");
    }

    public StoredFile moveToFinal(ExportTask task, Path temporary) throws IOException {
        String downloadName = "订单导出_" + FILE_TIME.format(task.getSnapshotTime()) + "_" + task.getTaskNo() + ".xlsx";
        String physicalName = task.getTaskNo() + "_" + shortToken(task.getExecutionToken()) + ".xlsx";
        Path target = finalDirectory.resolve(physicalName).normalize();
        ensureInside(finalDirectory, target);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target);
        }
        return new StoredFile(downloadName, root.relativize(target).toString().replace('\\', '/'), Files.size(target), target);
    }

    public Path resolveStored(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        ensureInside(finalDirectory, resolved);
        return resolved;
    }

    public void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public Path finalDirectory() { return finalDirectory; }
    public Path temporaryDirectory() { return temporaryDirectory; }

    private void ensureDiskSpace() throws IOException {
        if (Files.getFileStore(root).getUsableSpace() < properties.minimumFreeSpaceBytes()) {
            throw new BusinessException("INSUFFICIENT_DISK_SPACE", HttpStatus.UNPROCESSABLE_ENTITY,
                    "导出磁盘剩余空间不足");
        }
    }

    private void ensureInside(Path parent, Path child) {
        if (!child.startsWith(parent)) {
            throw new BusinessException("INVALID_FILE_PATH", HttpStatus.BAD_REQUEST, "文件路径非法");
        }
    }

    private String shortToken(String token) { return token == null ? "none" : token.replace("-", "").substring(0, 12); }

    public record StoredFile(String downloadName, String relativePath, long size, Path absolutePath) {}
}


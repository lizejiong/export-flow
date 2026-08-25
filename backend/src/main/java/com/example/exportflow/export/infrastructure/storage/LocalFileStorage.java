package com.example.exportflow.export.infrastructure.storage;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.export.domain.ExportTask;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        Path configuredRoot = properties.storageRoot().toAbsolutePath().normalize();
        rejectSymbolicLinkSegments(configuredRoot);
        Files.createDirectories(configuredRoot);
        rejectSymbolicLinkSegments(configuredRoot);
        root = configuredRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path temporary = root.resolve("tmp");
        Path files = root.resolve("files");
        Files.createDirectories(temporary);
        Files.createDirectories(files);
        if (Files.isSymbolicLink(temporary) || Files.isSymbolicLink(files)) throw invalidPath();
        temporaryDirectory = temporary.toRealPath(LinkOption.NOFOLLOW_LINKS);
        finalDirectory = files.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    public Path createTemporary(ExportTask task) throws IOException {
        ensureDiskSpace();
        ensureNoSymbolicLinks(temporaryDirectory);
        return Files.createTempFile(temporaryDirectory,
                task.getTaskNo() + "_" + shortToken(task.getExecutionToken()) + "_", ".xlsx.part");
    }

    public StoredFile moveToFinal(ExportTask task, Path temporary) throws IOException {
        String downloadName = "订单导出_" + FILE_TIME.format(task.getSnapshotTime()) + "_" + task.getTaskNo() + ".xlsx";
        String physicalName = task.getTaskNo() + "_" + shortToken(task.getExecutionToken()) + ".xlsx";
        Path source = temporary.toAbsolutePath().normalize();
        ensureInside(temporaryDirectory, source);
        ensureNoSymbolicLinks(source);
        Path target = finalDirectory.resolve(physicalName).normalize();
        ensureInside(finalDirectory, target);
        ensureNoSymbolicLinks(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
        return new StoredFile(downloadName, relativePath(target), Files.size(target), target);
    }

    public Path resolveStored(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        ensureInside(finalDirectory, resolved);
        ensureNoSymbolicLinks(resolved);
        return resolved;
    }

    public String relativePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        ensureInside(finalDirectory, normalized);
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    public void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (!normalized.startsWith(temporaryDirectory) && !normalized.startsWith(finalDirectory)) return;
            ensureNoSymbolicLinks(normalized);
            Files.deleteIfExists(normalized);
        } catch (IOException | BusinessException ignored) {
        }
    }

    public Path finalDirectory() {
        return finalDirectory;
    }

    public Path temporaryDirectory() {
        return temporaryDirectory;
    }

    private void ensureDiskSpace() throws IOException {
        if (Files.getFileStore(root).getUsableSpace() < properties.minimumFreeSpaceBytes()) {
            throw new BusinessException("INSUFFICIENT_DISK_SPACE", HttpStatus.UNPROCESSABLE_ENTITY,
                    "导出磁盘剩余空间不足");
        }
    }

    private void ensureNoSymbolicLinks(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        ensureInside(root, normalized);
        Path current = root;
        Path relative = root.relativize(normalized);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw invalidPath();
            }
        }
    }

    private void rejectSymbolicLinkSegments(Path path) {
        Path current = path.getRoot();
        if (current == null) throw invalidPath();
        for (Path part : path) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw invalidPath();
            }
        }
    }

    private void ensureInside(Path parent, Path child) {
        if (!child.startsWith(parent)) throw invalidPath();
    }

    private BusinessException invalidPath() {
        return new BusinessException("INVALID_FILE_PATH", HttpStatus.BAD_REQUEST, "文件路径非法");
    }

    private String shortToken(String token) {
        if (token == null) return "none";
        String compact = token.replace("-", "");
        return compact.substring(0, Math.min(12, compact.length()));
    }

    public record StoredFile(String downloadName, String relativePath, long size, Path absolutePath) {
    }
}

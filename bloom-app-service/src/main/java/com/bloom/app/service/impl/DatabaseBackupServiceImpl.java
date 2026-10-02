package com.bloom.app.service.impl;

import com.bloom.app.api.dto.response.backup.DatabaseBackupStatusResponse;
import com.bloom.app.api.dto.response.backup.DatabaseBackupTriggerResponse;
import com.bloom.app.domain.enums.DatabaseBackupState;
import com.bloom.app.domain.properties.BloomProperties;
import com.bloom.app.domain.properties.DatabaseBackupProperties;
import com.bloom.app.service.DatabaseBackupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class DatabaseBackupServiceImpl implements DatabaseBackupService {
    private static final String BACKUP_FILE_PREFIX = "bloom_app-";
    private static final String BACKUP_FILE_GLOB = BACKUP_FILE_PREFIX + "*.dump";
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final DatabaseBackupProperties backupProperties;
    private final BloomProperties bloomProperties;
    private final DataSourceProperties dataSourceProperties;
    private final ProcessCommandExecutor commandExecutor;
    private final TaskExecutor backupExecutor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<DatabaseBackupStatusResponse> status;

    public DatabaseBackupServiceImpl(
            DatabaseBackupProperties backupProperties,
            BloomProperties bloomProperties,
            DataSourceProperties dataSourceProperties,
            ProcessCommandExecutor commandExecutor,
            @Qualifier("databaseBackupExecutor") TaskExecutor backupExecutor,
            Clock clock) {
        this.backupProperties = backupProperties;
        this.bloomProperties = bloomProperties;
        this.dataSourceProperties = dataSourceProperties;
        this.commandExecutor = commandExecutor;
        this.backupExecutor = backupExecutor;
        this.clock = clock;
        this.status = new AtomicReference<>(DatabaseBackupStatusResponse.builder()
            .state(backupProperties.isEnabled()
                ? DatabaseBackupState.IDLE
                : DatabaseBackupState.DISABLED)
            .message(backupProperties.isEnabled()
                ? "No database backup has run since application startup."
                : "Database backups are disabled.")
            .build());
    }

    @Override
    public DatabaseBackupTriggerResponse triggerBackup() {
        Instant requestedAt = clock.instant();
        if (!backupProperties.isEnabled()) {
            return triggerResponse(false, DatabaseBackupState.DISABLED, requestedAt);
        }
        if (!running.compareAndSet(false, true)) {
            return triggerResponse(false, DatabaseBackupState.RUNNING, requestedAt);
        }

        DatabaseBackupStatusResponse previousStatus = status.get();
        status.set(DatabaseBackupStatusResponse.builder()
            .state(DatabaseBackupState.RUNNING)
            .startedAt(requestedAt)
            .lastSuccessfulAt(previousStatus.getLastSuccessfulAt())
            .lastBackupFile(previousStatus.getLastBackupFile())
            .message("Database backup is running.")
            .build());

        try {
            backupExecutor.execute(() -> runBackup(requestedAt));
        } catch (RuntimeException exception) {
            running.set(false);
            markFailed(requestedAt, "Could not start database backup: " + safeMessage(exception));
            throw exception;
        }
        return triggerResponse(true, DatabaseBackupState.RUNNING, requestedAt);
    }

    @Override
    public DatabaseBackupStatusResponse getStatus() {
        return status.get();
    }

    @Scheduled(
        cron = "${bloom.backup.cron:0 30 16 * * *}",
        zone = "${bloom.store-zone-id:Asia/Jakarta}"
    )
    public void runScheduledBackup() {
        DatabaseBackupTriggerResponse response = triggerBackup();
        if (!response.isAccepted() && response.getState() == DatabaseBackupState.RUNNING) {
            log.info("Skipping scheduled database backup because another backup is running");
        }
    }

    private void runBackup(Instant startedAt) {
        Path partialBackup = null;
        try {
            Path backupDirectory = backupProperties.getDirectory().toAbsolutePath().normalize();
            Files.createDirectories(backupDirectory);
            partialBackup = Files.createTempFile(
                backupDirectory,
                BACKUP_FILE_PREFIX,
                ".dump.part"
            );

            DatabaseConnection connection = parseConnection(dataSourceProperties.getUrl());
            runPgDump(connection, partialBackup);
            validateDump(partialBackup);

            Path completedBackup = backupDirectory.resolve(backupFileName(startedAt));
            moveCompletedBackup(partialBackup, completedBackup);
            partialBackup = null;
            deleteExpiredBackups(backupDirectory);

            Instant completedAt = clock.instant();
            status.set(DatabaseBackupStatusResponse.builder()
                .state(DatabaseBackupState.SUCCEEDED)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .lastSuccessfulAt(completedAt)
                .lastBackupFile(completedBackup.getFileName().toString())
                .message("Database backup completed successfully.")
                .build());
            log.info("Database backup completed: {}", completedBackup);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markFailed(startedAt, "Database backup was interrupted.");
            log.error("Database backup was interrupted", exception);
        } catch (Exception exception) {
            markFailed(startedAt, "Database backup failed: " + safeMessage(exception));
            log.error("Database backup failed", exception);
        } finally {
            deletePartialBackup(partialBackup);
            running.set(false);
        }
    }

    private void runPgDump(DatabaseConnection connection, Path partialBackup)
            throws IOException, InterruptedException {
        List<String> command = List.of(
            backupProperties.getPgDumpPath(),
            "--host=" + connection.host(),
            "--port=" + connection.port(),
            "--username=" + dataSourceProperties.getUsername(),
            "--no-password",
            "--format=custom",
            "--no-owner",
            "--file=" + partialBackup,
            connection.database()
        );
        ProcessCommandExecutor.CommandResult result = commandExecutor.execute(
            command,
            postgresEnvironment(),
            backupProperties.getCommandTimeout()
        );
        requireSuccess("pg_dump", result);
        if (!Files.isRegularFile(partialBackup) || Files.size(partialBackup) == 0) {
            throw new IOException("pg_dump completed without creating a non-empty backup file");
        }
    }

    private void validateDump(Path partialBackup) throws IOException, InterruptedException {
        List<String> command = List.of(
            backupProperties.getPgRestorePath(),
            "--list",
            partialBackup.toString()
        );
        ProcessCommandExecutor.CommandResult result = commandExecutor.execute(
            command,
            Map.of(),
            backupProperties.getCommandTimeout()
        );
        requireSuccess("pg_restore --list", result);
    }

    private void requireSuccess(
            String commandName,
            ProcessCommandExecutor.CommandResult result) throws IOException {
        if (result.exitCode() != 0) {
            String output = result.output().isBlank() ? "" : ": " + result.output();
            throw new IOException(commandName + " exited with code " + result.exitCode() + output);
        }
    }

    private Map<String, String> postgresEnvironment() {
        Map<String, String> environment = new HashMap<>();
        if (dataSourceProperties.getPassword() != null) {
            environment.put("PGPASSWORD", dataSourceProperties.getPassword());
        }
        return environment;
    }

    private DatabaseConnection parseConnection(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                "Database backups require a jdbc:postgresql://host[:port]/database URL"
            );
        }

        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String database = uri.getPath();
        if (uri.getHost() == null || database == null || database.length() <= 1) {
            throw new IllegalStateException("PostgreSQL JDBC URL is missing a host or database name");
        }
        return new DatabaseConnection(
            uri.getHost(),
            uri.getPort() == -1 ? 5432 : uri.getPort(),
            database.substring(1)
        );
    }

    private String backupFileName(Instant startedAt) {
        ZoneId zoneId = bloomProperties.getStoreZoneId();
        return BACKUP_FILE_PREFIX
            + FILE_TIMESTAMP_FORMAT.withZone(zoneId).format(startedAt)
            + ".dump";
    }

    private void moveCompletedBackup(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private void deleteExpiredBackups(Path backupDirectory) {
        Instant cutoff = clock.instant().minusSeconds(backupProperties.getRetentionDays() * 86_400L);
        try (DirectoryStream<Path> backups = Files.newDirectoryStream(
                backupDirectory,
                BACKUP_FILE_GLOB)) {
            for (Path backup : backups) {
                try {
                    if (Files.getLastModifiedTime(backup).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(backup);
                        log.info("Deleted expired database backup: {}", backup);
                    }
                } catch (IOException exception) {
                    log.warn("Could not inspect or delete expired database backup: {}", backup,
                        exception);
                }
            }
        } catch (IOException exception) {
            log.warn("Could not scan database backup directory for expired files: {}",
                backupDirectory, exception);
        }
    }

    private void deletePartialBackup(Path partialBackup) {
        if (partialBackup == null) {
            return;
        }
        try {
            Files.deleteIfExists(partialBackup);
        } catch (IOException exception) {
            log.warn("Could not delete incomplete database backup: {}", partialBackup, exception);
        }
    }

    private void markFailed(Instant startedAt, String message) {
        DatabaseBackupStatusResponse previousStatus = status.get();
        status.set(DatabaseBackupStatusResponse.builder()
            .state(DatabaseBackupState.FAILED)
            .startedAt(startedAt)
            .completedAt(clock.instant())
            .lastSuccessfulAt(previousStatus.getLastSuccessfulAt())
            .lastBackupFile(previousStatus.getLastBackupFile())
            .message(message)
            .build());
    }

    private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message;
    }

    private DatabaseBackupTriggerResponse triggerResponse(
            boolean accepted,
            DatabaseBackupState state,
            Instant requestedAt) {
        return DatabaseBackupTriggerResponse.builder()
            .accepted(accepted)
            .state(state)
            .requestedAt(requestedAt)
            .build();
    }

    private record DatabaseConnection(String host, int port, String database) {
    }
}

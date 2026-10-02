package com.bloom.app.service.impl;

import com.bloom.app.api.dto.response.backup.DatabaseBackupTriggerResponse;
import com.bloom.app.domain.enums.DatabaseBackupState;
import com.bloom.app.domain.properties.BloomProperties;
import com.bloom.app.domain.properties.DatabaseBackupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.task.TaskExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseBackupServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-02T01:00:00Z");
    private static final String PASSWORD = "not-on-the-command-line";

    @TempDir
    Path backupDirectory;

    private DatabaseBackupProperties backupProperties;
    private DataSourceProperties dataSourceProperties;
    private ProcessCommandExecutor commandExecutor;

    @BeforeEach
    void setUp() {
        backupProperties = DatabaseBackupProperties.builder()
            .directory(backupDirectory)
            .pgDumpPath("pg_dump_test")
            .pgRestorePath("pg_restore_test")
            .retentionDays(30)
            .commandTimeout(Duration.ofMinutes(5))
            .build();
        dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:postgresql://127.0.0.1:5433/bloom_app");
        dataSourceProperties.setUsername("bloom_user");
        dataSourceProperties.setPassword(PASSWORD);
        commandExecutor = mock(ProcessCommandExecutor.class);
    }

    @Test
    void createsValidBackupWithoutPuttingPasswordOnCommandLine() throws Exception {
        mockSuccessfulCommands();
        DatabaseBackupServiceImpl service = service(Runnable::run);

        DatabaseBackupTriggerResponse trigger = service.triggerBackup();

        assertThat(trigger.isAccepted()).isTrue();
        assertThat(trigger.getState()).isEqualTo(DatabaseBackupState.RUNNING);
        assertThat(service.getStatus().getState()).isEqualTo(DatabaseBackupState.SUCCEEDED);
        assertThat(service.getStatus().getLastBackupFile())
            .isEqualTo("bloom_app-20261002-080000-000.dump");
        assertThat(backupDirectory.resolve(service.getStatus().getLastBackupFile()))
            .hasContent("database dump");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> environmentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(commandExecutor, times(2)).execute(
            commandCaptor.capture(),
            environmentCaptor.capture(),
            any(Duration.class)
        );

        List<String> dumpCommand = commandCaptor.getAllValues().getFirst();
        assertThat(dumpCommand).contains(
            "pg_dump_test",
            "--host=127.0.0.1",
            "--port=5433",
            "--username=bloom_user",
            "--no-password",
            "--format=custom",
            "--no-owner",
            "bloom_app"
        );
        assertThat(dumpCommand).noneMatch(argument -> argument.contains(PASSWORD));
        assertThat(environmentCaptor.getAllValues().getFirst())
            .containsEntry("PGPASSWORD", PASSWORD);

        List<String> validationCommand = commandCaptor.getAllValues().get(1);
        assertThat(validationCommand.getFirst()).isEqualTo("pg_restore_test");
        assertThat(validationCommand).contains("--list");
    }

    @Test
    void deletesOnlyBloomBackupsOlderThanThirtyDays() throws Exception {
        Path expired = Files.writeString(
            backupDirectory.resolve("bloom_app-20260801-020000-000.dump"),
            "old"
        );
        Path recent = Files.writeString(
            backupDirectory.resolve("bloom_app-20260920-020000-000.dump"),
            "recent"
        );
        Path unrelated = Files.writeString(backupDirectory.resolve("family-photo.dump"), "keep");
        Files.setLastModifiedTime(expired, FileTime.from(NOW.minus(Duration.ofDays(31))));
        Files.setLastModifiedTime(recent, FileTime.from(NOW.minus(Duration.ofDays(12))));
        Files.setLastModifiedTime(unrelated, FileTime.from(NOW.minus(Duration.ofDays(60))));
        mockSuccessfulCommands();

        service(Runnable::run).triggerBackup();

        assertThat(expired).doesNotExist();
        assertThat(recent).exists();
        assertThat(unrelated).exists();
    }

    @Test
    void failedDumpIsReportedAndPartialFileIsRemoved() throws Exception {
        when(commandExecutor.execute(anyList(), anyMap(), any(Duration.class)))
            .thenReturn(new ProcessCommandExecutor.CommandResult(1, "connection failed"));
        DatabaseBackupServiceImpl service = service(Runnable::run);

        service.triggerBackup();

        assertThat(service.getStatus().getState()).isEqualTo(DatabaseBackupState.FAILED);
        assertThat(service.getStatus().getMessage()).contains("pg_dump exited with code 1");
        try (var files = Files.list(backupDirectory)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void rejectsSecondTriggerWhileBackupIsRunning() throws Exception {
        mockSuccessfulCommands();
        AtomicReference<Runnable> pendingBackup = new AtomicReference<>();
        TaskExecutor deferredExecutor = pendingBackup::set;
        DatabaseBackupServiceImpl service = service(deferredExecutor);

        DatabaseBackupTriggerResponse first = service.triggerBackup();
        DatabaseBackupTriggerResponse second = service.triggerBackup();

        assertThat(first.isAccepted()).isTrue();
        assertThat(second.isAccepted()).isFalse();
        assertThat(second.getState()).isEqualTo(DatabaseBackupState.RUNNING);
        pendingBackup.get().run();
        assertThat(service.getStatus().getState()).isEqualTo(DatabaseBackupState.SUCCEEDED);
    }

    @Test
    void reportsDisabledWithoutSubmittingWork() {
        backupProperties.setEnabled(false);
        TaskExecutor executor = mock(TaskExecutor.class);
        DatabaseBackupServiceImpl service = service(executor);

        DatabaseBackupTriggerResponse response = service.triggerBackup();

        assertThat(response.isAccepted()).isFalse();
        assertThat(response.getState()).isEqualTo(DatabaseBackupState.DISABLED);
        assertThat(service.getStatus().getState()).isEqualTo(DatabaseBackupState.DISABLED);
    }

    private DatabaseBackupServiceImpl service(TaskExecutor taskExecutor) {
        BloomProperties bloomProperties = BloomProperties.builder()
            .storeZoneId(ZoneId.of("Asia/Jakarta"))
            .build();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new DatabaseBackupServiceImpl(
            backupProperties,
            bloomProperties,
            dataSourceProperties,
            commandExecutor,
            taskExecutor,
            clock
        );
    }

    private void mockSuccessfulCommands() throws Exception {
        when(commandExecutor.execute(anyList(), anyMap(), any(Duration.class)))
            .thenAnswer(invocation -> {
                List<String> command = invocation.getArgument(0);
                if (command.getFirst().equals("pg_dump_test")) {
                    String outputArgument = command.stream()
                        .filter(argument -> argument.startsWith("--file="))
                        .findFirst()
                        .orElseThrow();
                    Files.writeString(Path.of(outputArgument.substring("--file=".length())),
                        "database dump");
                }
                return new ProcessCommandExecutor.CommandResult(0, "");
            });
    }
}

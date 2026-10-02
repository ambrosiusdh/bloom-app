package com.bloom.app.domain.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "bloom.backup")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DatabaseBackupProperties {
    @Builder.Default
    private boolean enabled = true;

    @NotNull
    @Builder.Default
    private Path directory = Path.of("backups");

    @NotBlank
    @Builder.Default
    private String pgDumpPath = "pg_dump";

    @NotBlank
    @Builder.Default
    private String pgRestorePath = "pg_restore";

    @Min(1)
    @Builder.Default
    private int retentionDays = 30;

    @NotBlank
    @Builder.Default
    private String cron = "0 30 16 * * *";

    @NotNull
    @Builder.Default
    private Duration commandTimeout = Duration.ofMinutes(30);
}

package com.bloom.app.api.dto.response.backup;

import com.bloom.app.domain.enums.DatabaseBackupState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class DatabaseBackupStatusResponse {
    private DatabaseBackupState state;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastSuccessfulAt;
    private String lastBackupFile;
    private String message;
}

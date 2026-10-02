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
@Builder
public class DatabaseBackupTriggerResponse {
    private boolean accepted;
    private DatabaseBackupState state;
    private Instant requestedAt;
}

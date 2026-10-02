package com.bloom.app.service;

import com.bloom.app.api.dto.response.backup.DatabaseBackupStatusResponse;
import com.bloom.app.api.dto.response.backup.DatabaseBackupTriggerResponse;

public interface DatabaseBackupService {
    DatabaseBackupTriggerResponse triggerBackup();

    DatabaseBackupStatusResponse getStatus();
}

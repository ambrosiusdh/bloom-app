package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.backup.DatabaseBackupStatusResponse;
import com.bloom.app.api.dto.response.backup.DatabaseBackupTriggerResponse;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.enums.DatabaseBackupState;
import com.bloom.app.service.DatabaseBackupService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class DatabaseBackupController {
    private final DatabaseBackupService databaseBackupService;

    @PostMapping
    @Operation(
        summary = "Start a database backup",
        description = "Starts a PostgreSQL backup asynchronously. Every authenticated Bloom user "
            + "may call this endpoint."
    )
    public ResponseEntity<ApiResponse<DatabaseBackupTriggerResponse>> triggerBackup() {
        DatabaseBackupTriggerResponse response = databaseBackupService.triggerBackup();
        if (response.isAccepted()) {
            return ResponseHelper.accepted("Database backup accepted.", response);
        }
        if (response.getState() == DatabaseBackupState.DISABLED) {
            return ResponseHelper.serviceUnavailable("Database backups are disabled.");
        }
        return ResponseHelper.conflict("A database backup is already running.");
    }

    @GetMapping("/status")
    @Operation(
        summary = "Get database backup status",
        description = "Returns the current or most recent database backup result."
    )
    public ResponseEntity<ApiResponse<DatabaseBackupStatusResponse>> getStatus() {
        return ResponseHelper.ok(databaseBackupService.getStatus());
    }
}

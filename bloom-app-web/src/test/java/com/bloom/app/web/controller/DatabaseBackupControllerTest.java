package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.backup.DatabaseBackupStatusResponse;
import com.bloom.app.api.dto.response.backup.DatabaseBackupTriggerResponse;
import com.bloom.app.domain.enums.DatabaseBackupState;
import com.bloom.app.service.DatabaseBackupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatabaseBackupControllerTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-02T01:00:00Z");

    private DatabaseBackupService databaseBackupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        databaseBackupService = mock(DatabaseBackupService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new DatabaseBackupController(databaseBackupService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void acceptsManualBackupTrigger() throws Exception {
        when(databaseBackupService.triggerBackup()).thenReturn(trigger(true,
            DatabaseBackupState.RUNNING));

        mockMvc.perform(post("/api/backups"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value(202))
            .andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.state").value("RUNNING"))
            .andExpect(jsonPath("$.data.requestedAt").value(REQUESTED_AT.toString()));
    }

    @Test
    void reportsConflictWhenBackupIsAlreadyRunning() throws Exception {
        when(databaseBackupService.triggerBackup()).thenReturn(trigger(false,
            DatabaseBackupState.RUNNING));

        mockMvc.perform(post("/api/backups"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void returnsLatestBackupStatus() throws Exception {
        when(databaseBackupService.getStatus()).thenReturn(DatabaseBackupStatusResponse.builder()
            .state(DatabaseBackupState.SUCCEEDED)
            .completedAt(REQUESTED_AT)
            .lastSuccessfulAt(REQUESTED_AT)
            .lastBackupFile("bloom_app-20261002-080000-000.dump")
            .message("Database backup completed successfully.")
            .build());

        mockMvc.perform(get("/api/backups/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.lastBackupFile")
                .value("bloom_app-20261002-080000-000.dump"));
    }

    private DatabaseBackupTriggerResponse trigger(
            boolean accepted,
            DatabaseBackupState state) {
        return DatabaseBackupTriggerResponse.builder()
            .accepted(accepted)
            .state(state)
            .requestedAt(REQUESTED_AT)
            .build();
    }
}

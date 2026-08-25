package com.bloom.app.web.controller;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.cashsession.CashReconciliationResponse;
import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.CashSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/cash-sessions")
@RequiredArgsConstructor
public class CashSessionController {
    private static final String NO_OPEN_SESSION_MESSAGE =
        "No cash session is currently open";

    private final CashSessionService cashSessionService;

    @PostMapping("/open")
    @Operation(summary = "Open the global cash session")
    public ResponseEntity<ApiResponse<CashSessionResponse>> openSession(
            @Valid @RequestBody OpenCashSessionRequest request) {
        return ResponseHelper.created(
            "Cash session opened successfully", cashSessionService.openSession(request));
    }

    @GetMapping("/current")
    @Operation(
        summary = "Get the globally open cash session",
        description = "Returns the open cash session when present. Because this is a zero-or-one "
            + "state, HTTP 200 with data null is returned when no session is open."
    )
    public ResponseEntity<ApiResponse<CashSessionResponse>> getCurrentSession() {
        return cashSessionService.getCurrentSession()
            .map(ResponseHelper::ok)
            .orElseGet(() -> ResponseHelper.ok(NO_OPEN_SESSION_MESSAGE, null));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get cash session details")
    public ResponseEntity<ApiResponse<CashSessionResponse>> getSessionDetails(
            @PathVariable @Positive Long sessionId) {
        return ResponseHelper.ok(cashSessionService.getSessionDetails(sessionId));
    }

    @GetMapping("/{sessionId}/movements")
    @Operation(summary = "Get a paginated cash movement ledger")
    public ResponseEntity<ApiResponse<Page<CashMovementResponse>>> getSessionMovements(
            @PathVariable @Positive Long sessionId,
            Pageable pageable) {
        return ResponseHelper.ok(cashSessionService.getSessionMovements(
            sessionId, PagingHelper.toPageRequest(pageable)));
    }

    @GetMapping("/{sessionId}/expected-cash")
    @Operation(summary = "Recalculate expected drawer cash from the ledger")
    public ResponseEntity<ApiResponse<CashReconciliationResponse>> calculateExpectedCash(
            @PathVariable @Positive Long sessionId) {
        return ResponseHelper.ok(cashSessionService.calculateExpectedCash(sessionId));
    }

    @PostMapping("/{sessionId}/close")
    @Operation(summary = "Close and reconcile the global cash session")
    public ResponseEntity<ApiResponse<CashSessionResponse>> closeSession(
            @PathVariable @Positive Long sessionId,
            @Valid @RequestBody CloseCashSessionRequest request) {
        return ResponseHelper.ok(cashSessionService.closeSession(sessionId, request));
    }
}

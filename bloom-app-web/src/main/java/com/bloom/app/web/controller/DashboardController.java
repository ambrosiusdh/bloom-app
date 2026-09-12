package com.bloom.app.web.controller;

import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardResponse;
import com.bloom.app.api.dto.response.dashboard.OperationalDashboardResponse;
import com.bloom.app.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(
        summary = "Get dashboard overview",
        description = "Retrieve summary statistics, revenue charts, recent transactions, top categories, and low stock items for the dashboard."
    )
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboardOverview() {
        DashboardResponse response = dashboardService.getDashboardOverview();
        return ResponseHelper.ok(response);
    }

    @GetMapping("/operational-overview")
    @Operation(
        summary = "Get the Release 1 operational dashboard",
        description = "Returns sales today, current drawer-session operations, and supplier payables. " +
            "Every authenticated Bloom user may read this endpoint."
    )
    public ResponseEntity<ApiResponse<OperationalDashboardResponse>> getOperationalOverview() {
        return ResponseHelper.ok(dashboardService.getOperationalOverview());
    }
}

package com.bloom.app.service;

import com.bloom.app.api.dto.response.dashboard.DashboardResponse;
import com.bloom.app.api.dto.response.dashboard.OperationalDashboardResponse;

public interface DashboardService {
    DashboardResponse getDashboardOverview();

    OperationalDashboardResponse getOperationalOverview();
}

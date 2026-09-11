package com.bloom.app.api.dto.response.dashboard;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class DashboardDrillDownResponse {
    DashboardDrillDownDestination destination;
    String reference;
    LocalDate startDate;
    LocalDate endDate;
}

package com.bloom.app.api.dto.response.dashboard;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;

@Value
@Builder
public class OperationalDashboardResponse {
    Instant asOf;
    Instant freshUntil;
    LocalDate businessDate;
    String storeZoneId;
    DashboardSalesTodayResponse salesToday;
    DashboardCurrentCashSessionResponse currentCashSession;
    DashboardSupplierPayablesResponse supplierPayables;
}

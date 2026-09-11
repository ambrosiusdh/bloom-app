package com.bloom.app.domain.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Data
@Validated
@ConfigurationProperties(prefix = "bloom.dashboard")
public class DashboardProperties {
    @NotNull
    private ZoneId storeZoneId = ZoneId.of("Asia/Jakarta");

    @NotNull
    private Duration freshness = Duration.ofMinutes(5);

    @AssertTrue(message = "bloom.dashboard.freshness must be greater than zero")
    public boolean isFreshnessPositive() {
        return freshness != null && !freshness.isZero() && !freshness.isNegative();
    }
}

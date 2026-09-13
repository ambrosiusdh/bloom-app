package com.bloom.app.domain.properties;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "bloom")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BloomProperties {
    private BigDecimal lowStockThreshold;

    @NotNull
    @Builder.Default
    private ZoneId storeZoneId = ZoneId.of("Asia/Jakarta");
}

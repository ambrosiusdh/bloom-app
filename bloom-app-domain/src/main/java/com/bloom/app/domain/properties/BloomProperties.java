package com.bloom.app.domain.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "bloom")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BloomProperties {
    private BigDecimal lowStockThreshold;
}

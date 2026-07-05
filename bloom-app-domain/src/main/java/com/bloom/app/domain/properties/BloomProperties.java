package com.bloom.app.domain.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bloom")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BloomProperties {
    private Integer lowStockThreshold;
}

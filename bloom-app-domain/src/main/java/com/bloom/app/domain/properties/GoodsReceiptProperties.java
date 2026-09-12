package com.bloom.app.domain.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Data
@Validated
@ConfigurationProperties(prefix = "bloom.goods-receipt")
public class GoodsReceiptProperties {
    @NotNull
    private ZoneId storeZoneId = ZoneId.of("Asia/Jakarta");
}

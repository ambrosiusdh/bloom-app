package com.bloom.app.api.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockDto {
    private Long id;
    private String name;
    private String sku;
    private BigDecimal stock;
    private BigDecimal minStock;
    private String category;
}

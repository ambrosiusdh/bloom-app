package com.bloom.app.api.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockDto {
    private Long id;
    private String name;
    private String sku;
    private Integer stock;
    private Integer minStock;
    private String category;
}

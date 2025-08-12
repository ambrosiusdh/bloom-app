package com.bloom.app.domain.dto.request.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FilterItemRequest {
    private String sku;
    private String name;
    private String category;
    private String skuOrName;
}

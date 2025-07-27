package com.bloom.app.domain.dto.request.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateItemRequest {
    private String name;
    private String sku;
    private String description;
    private Double price;
    private Integer stockQuantity;
}

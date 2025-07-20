package com.bloom.app.domain.dto.request.item;

import jakarta.validation.constraints.NotBlank;
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

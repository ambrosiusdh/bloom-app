package com.bloom.app.domain.dto.response.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemResponse {
    private String name;
    private String sku;
    private String description;
    private Double price;
    private Integer stockQuantity;

    private Instant createdAt;
    private Instant updatedAt;

    private String createdBy;
    private String updatedBy;
}

package com.bloom.app.domain.dto.request.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateItemRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String sku;

    private String description;

    @NotNull
    private Double price;

    @NotNull
    private Integer stockQuantity;
}

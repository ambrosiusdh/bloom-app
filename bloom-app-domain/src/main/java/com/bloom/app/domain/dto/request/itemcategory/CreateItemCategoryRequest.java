package com.bloom.app.domain.dto.request.itemcategory;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateItemCategoryRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;
}

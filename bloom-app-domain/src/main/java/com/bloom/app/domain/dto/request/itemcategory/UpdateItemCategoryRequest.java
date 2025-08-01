package com.bloom.app.domain.dto.request.itemcategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateItemCategoryRequest {
    private String name;
    private String description;
}

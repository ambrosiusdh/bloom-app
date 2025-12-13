package com.bloom.app.api.dto.request.itemcategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterItemCategoryRequest {
    private String code;
    private String name;
}

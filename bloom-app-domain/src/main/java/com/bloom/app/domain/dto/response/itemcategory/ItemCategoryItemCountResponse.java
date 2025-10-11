package com.bloom.app.domain.dto.response.itemcategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemCategoryItemCountResponse {
    private String code;
    private Long itemCount;
}

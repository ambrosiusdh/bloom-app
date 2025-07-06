package com.bloom.app.domain.dto.response.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemResponse {
    private String itemCode;
    private String itemName;
    private String itemDescription;
    private Double itemPrice;
}

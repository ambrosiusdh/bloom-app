package com.bloom.app.api.dto.response.goodsreceipt;

import com.bloom.app.api.dto.response.item.ItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItemResponse {
    private ItemResponse item;
    private Integer quantity;
}

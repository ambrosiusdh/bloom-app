package com.bloom.app.domain.dto.response.goodsreceipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItemResponse {

    private Long id;
    private Long itemId;
    private String itemName;
    private String itemSku;
    private Integer quantity;

}

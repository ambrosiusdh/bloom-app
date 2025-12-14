package com.bloom.app.api.dto.request.goodsreceipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterGoodsReceiptRequest {
    private String code;
    private String supplierName;
    private Instant receivedDateFrom;
    private Instant receivedDateTo;
}

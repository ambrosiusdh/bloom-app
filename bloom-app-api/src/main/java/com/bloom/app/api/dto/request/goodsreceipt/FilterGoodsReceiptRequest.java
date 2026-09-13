package com.bloom.app.api.dto.request.goodsreceipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterGoodsReceiptRequest {
    private String code;
    private String supplierName;
    private LocalDate receivedDateFrom;
    private LocalDate receivedDateTo;
}

package com.bloom.app.domain.dto.response.goodsreceipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptResponse {

    private Long id;
    private String code;
    private Instant receivedDate;
    private String supplierName;
    private String description;
    private Instant createdAt;
    private String createdBy;
    private List<GoodsReceiptItemResponse> items;
}

package com.bloom.app.api.dto.request.goodsreceipt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class CreateGoodsReceiptRequest {

    private Instant receivedDate;
    private String supplierName;
    private String description;

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    private List<GoodsReceiptItemRequest> items;
}

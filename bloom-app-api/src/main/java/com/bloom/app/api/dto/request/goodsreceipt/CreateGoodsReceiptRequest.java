package com.bloom.app.api.dto.request.goodsreceipt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoodsReceiptRequest {

    private Instant receivedDate;
    
    @NotNull(message = "Supplier Code is required")
    private String supplierCode;
    
    private String description;

    @NotNull(message = "Total amount is required")
    private BigDecimal totalAmount;

    @NotNull(message = "Paid amount is required")
    private BigDecimal paidAmount;

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    private List<CreateGoodsReceiptItemRequest> items;
}

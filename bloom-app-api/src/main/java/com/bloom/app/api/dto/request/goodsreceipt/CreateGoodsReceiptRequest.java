package com.bloom.app.api.dto.request.goodsreceipt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Received date is required")
    private Instant receivedDate;
    
    @NotBlank(message = "Supplier Code is required")
    private String supplierCode;
    
    private String description;

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    private List<CreateGoodsReceiptItemRequest> items;
}

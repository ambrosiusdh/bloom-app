package com.bloom.app.api.dto.request.goodsreceipt;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelGoodsReceiptRequest {
    @NotBlank(message = "Cancellation reason is required")
    private String reason;
}

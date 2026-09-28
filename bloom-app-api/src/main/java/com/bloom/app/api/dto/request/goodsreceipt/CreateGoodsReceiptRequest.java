package com.bloom.app.api.dto.request.goodsreceipt;

import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.domain.validation.SupplierCodePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(
        max = SupplierCodePolicy.MAX_LENGTH,
        message = "Supplier code must not exceed "
            + SupplierCodePolicy.MAX_LENGTH + " characters"
    )
    private String supplierCode;
    
    private String description;

    @Valid
    private CreateSupplierPaymentRequest initialPayment;

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    private List<CreateGoodsReceiptItemRequest> items;
}

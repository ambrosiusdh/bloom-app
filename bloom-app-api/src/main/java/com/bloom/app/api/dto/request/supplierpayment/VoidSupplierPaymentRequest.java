package com.bloom.app.api.dto.request.supplierpayment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoidSupplierPaymentRequest {
    @NotBlank(message = "Void reason is required")
    @Size(max = 255, message = "Void reason must not exceed 255 characters")
    private String reason;
}

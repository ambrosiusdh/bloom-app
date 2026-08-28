package com.bloom.app.api.dto.request.supplierpayment;

import com.bloom.app.domain.enums.SupplierPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSupplierPaymentRequest {
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.0000", inclusive = false, message = "Payment amount must be positive")
    @Digits(integer = 15, fraction = 4, message = "Payment amount must fit NUMERIC(19,4)")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private SupplierPaymentMethod paymentMethod;

    @NotNull(message = "Paid at is required")
    @PastOrPresent(message = "Paid at must not be in the future")
    private Instant paidAt;

    @Size(max = 255, message = "Payment reference must not exceed 255 characters")
    private String reference;

    @Size(max = 255, message = "Payment note must not exceed 255 characters")
    private String note;
}

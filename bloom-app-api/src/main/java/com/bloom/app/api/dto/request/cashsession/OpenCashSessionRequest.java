package com.bloom.app.api.dto.request.cashsession;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenCashSessionRequest {
    @NotNull(message = "Opening cash is required")
    @DecimalMin(value = "0.0000", message = "Opening cash must not be negative")
    @Digits(integer = 15, fraction = 4, message = "Opening cash must have at most 15 integer and 4 decimal digits")
    private BigDecimal openingCash;
}

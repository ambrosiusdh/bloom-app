package com.bloom.app.domain.dto.request.print;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PrintReceiptRequest {
    @NotBlank
    private String saleCode;
}

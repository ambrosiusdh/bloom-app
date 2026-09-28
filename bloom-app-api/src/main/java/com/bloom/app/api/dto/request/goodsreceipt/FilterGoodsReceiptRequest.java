package com.bloom.app.api.dto.request.goodsreceipt;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterGoodsReceiptRequest {
    private String code;

    @Size(max = 255, message = "Supplier code filter must not exceed 255 characters")
    private String supplierCode;

    private String supplierName;
    private LocalDate receivedDateFrom;
    private LocalDate receivedDateTo;
}

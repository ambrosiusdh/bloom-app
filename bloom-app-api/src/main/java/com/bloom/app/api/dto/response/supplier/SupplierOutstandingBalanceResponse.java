package com.bloom.app.api.dto.response.supplier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierOutstandingBalanceResponse {
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private BigDecimal totalPostedAmount;
    private BigDecimal validPayments;
    private BigDecimal outstandingAmount;
}

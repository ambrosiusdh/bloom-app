package com.bloom.app.api.dto.response.supplierpayment;

import com.bloom.app.domain.enums.SupplierPaymentMethod;
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
public class SupplierPaymentResponse {
    private Long id;
    private Long receiptId;
    private String receiptCode;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private Long cashSessionId;
    private BigDecimal amount;
    private SupplierPaymentMethod paymentMethod;
    private Instant paidAt;
    private String reference;
    private String note;
    private String actor;
    private boolean voided;
    private String voidReason;
    private Instant voidedAt;
    private String voidedBy;
    private String idempotencyKey;
    private Instant createdAt;
}

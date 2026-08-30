package com.bloom.app.api.dto.response.goodsreceipt;

import com.bloom.app.domain.enums.GoodsReceiptStatus;
import com.bloom.app.domain.enums.SupplierPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptResponse {

    private Long id;
    private String code;
    private Instant receivedDate;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private SupplierPaymentStatus paymentStatus;
    private GoodsReceiptStatus status;
    private String description;
    private Instant cancelledAt;
    private String cancelledBy;
    private String cancellationReason;
    private Instant createdAt;
    private String createdBy;
    private List<GoodsReceiptItemResponse> items;
}

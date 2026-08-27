package com.bloom.app.service;

import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.api.dto.request.supplierpayment.VoidSupplierPaymentRequest;
import com.bloom.app.api.dto.response.supplierpayment.SupplierPaymentResponse;
import com.bloom.app.domain.model.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SupplierPaymentService {
    SupplierPaymentResponse createPayment(
        String receiptCode, String idempotencyKey, CreateSupplierPaymentRequest request);

    SupplierPaymentResponse createInitialPayment(
        GoodsReceipt receipt, String idempotencyKey, CreateSupplierPaymentRequest request);

    SupplierPaymentResponse voidPayment(
        Long paymentId, VoidSupplierPaymentRequest request);

    Page<SupplierPaymentResponse> getReceiptPaymentHistory(
        String receiptCode, Pageable pageable);
}

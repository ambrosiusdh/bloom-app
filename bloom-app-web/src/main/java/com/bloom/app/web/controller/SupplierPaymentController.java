package com.bloom.app.web.controller;

import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.api.dto.request.supplierpayment.VoidSupplierPaymentRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.supplierpayment.SupplierPaymentResponse;
import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.SupplierPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class SupplierPaymentController {
    private final SupplierPaymentService supplierPaymentService;

    @PostMapping("/api/goods-receipts/{code}/payments")
    @Operation(summary = "Record a supplier payment against a goods receipt")
    public ResponseEntity<ApiResponse<SupplierPaymentResponse>> createPayment(
            @PathVariable @NotBlank @Size(max = 100) String code,
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key header is required")
            @Size(max = 100, message = "Idempotency-Key must not exceed 100 characters")
            String idempotencyKey,
            @Valid @RequestBody CreateSupplierPaymentRequest request) {
        SupplierPaymentResponse response = supplierPaymentService
            .createPayment(code, idempotencyKey, request);
        return ResponseHelper.created("Supplier payment recorded successfully", response);
    }

    @GetMapping("/api/goods-receipts/{code}/payments")
    @Operation(summary = "Get a receipt's complete supplier-payment history")
    public ResponseEntity<ApiResponse<Page<SupplierPaymentResponse>>> getPaymentHistory(
            @PathVariable @NotBlank @Size(max = 100) String code,
            Pageable pageable) {
        return ResponseHelper.ok(supplierPaymentService.getReceiptPaymentHistory(
            code, PagingHelper.toPageRequest(pageable)));
    }

    @PostMapping("/api/supplier-payments/{paymentId}/void")
    @Operation(summary = "Void a supplier payment without deleting its audit history")
    public ResponseEntity<ApiResponse<SupplierPaymentResponse>> voidPayment(
            @PathVariable @Positive Long paymentId,
            @Valid @RequestBody VoidSupplierPaymentRequest request) {
        return ResponseHelper.ok(supplierPaymentService.voidPayment(paymentId, request));
    }
}

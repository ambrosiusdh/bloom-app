package com.bloom.app.web.controller;

import com.bloom.app.api.dto.request.supplier.CreateSupplierRequest;
import com.bloom.app.api.dto.request.supplier.FilterSupplierRequest;
import com.bloom.app.api.dto.request.supplier.SetSupplierActiveRequest;
import com.bloom.app.api.dto.request.supplier.UpdateSupplierRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.supplier.SupplierResponse;
import com.bloom.app.api.dto.response.supplier.SupplierOutstandingBalanceResponse;
import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Validated
public class SupplierController {
    private final SupplierService supplierService;

    @GetMapping
    @Operation(summary = "Filter and list suppliers")
    public ResponseEntity<ApiResponse<Page<SupplierResponse>>> filterSuppliers(
        @Valid FilterSupplierRequest request,
        Pageable pageable
    ) {
        Page<SupplierResponse> response = supplierService.filterSuppliers(
            request,
            PagingHelper.toPageRequest(pageable)
        );
        return ResponseHelper.ok(response);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get supplier details")
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplierDetails(
        @PathVariable @NotBlank @Size(max = 255) String code
    ) {
        return ResponseHelper.ok(supplierService.getSupplierDetails(code));
    }

    @GetMapping("/{code}/outstanding-balance")
    @Operation(summary = "Get a supplier's derived outstanding accounts-payable balance")
    public ResponseEntity<ApiResponse<SupplierOutstandingBalanceResponse>> getOutstandingBalance(
        @PathVariable @NotBlank @Size(max = 255) String code
    ) {
        return ResponseHelper.ok(supplierService.getOutstandingBalance(code));
    }

    @PostMapping
    @Operation(summary = "Create a supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
        @Valid @RequestBody CreateSupplierRequest request
    ) {
        return ResponseHelper.ok(supplierService.createSupplier(request));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update supplier master data")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
        @PathVariable @NotBlank @Size(max = 255) String code,
        @Valid @RequestBody UpdateSupplierRequest request
    ) {
        return ResponseHelper.ok(supplierService.updateSupplier(code, request));
    }

    @PatchMapping("/{code}/activation")
    @Operation(summary = "Deactivate or reactivate a supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> setSupplierActive(
        @PathVariable @NotBlank @Size(max = 255) String code,
        @Valid @RequestBody SetSupplierActiveRequest request
    ) {
        return ResponseHelper.ok(supplierService.setSupplierActive(code, request.getActive()));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Delete an unused supplier")
    public ResponseEntity<ApiResponse<Boolean>> deleteSupplier(
        @PathVariable @NotBlank @Size(max = 255) String code
    ) {
        supplierService.deleteSupplier(code);
        return ResponseHelper.ok(Boolean.TRUE);
    }
}

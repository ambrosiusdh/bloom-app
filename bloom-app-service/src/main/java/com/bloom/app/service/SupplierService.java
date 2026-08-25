package com.bloom.app.service;

import com.bloom.app.api.dto.request.supplier.CreateSupplierRequest;
import com.bloom.app.api.dto.request.supplier.FilterSupplierRequest;
import com.bloom.app.api.dto.request.supplier.UpdateSupplierRequest;
import com.bloom.app.api.dto.response.supplier.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SupplierService {
    SupplierResponse createSupplier(CreateSupplierRequest request);

    SupplierResponse updateSupplier(String code, UpdateSupplierRequest request);

    Page<SupplierResponse> filterSuppliers(FilterSupplierRequest request, Pageable pageable);

    SupplierResponse getSupplierDetails(String code);

    SupplierResponse setSupplierActive(String code, boolean active);

    void deleteSupplier(String code);
}

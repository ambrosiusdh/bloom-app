package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.supplier.CreateSupplierRequest;
import com.bloom.app.api.dto.request.supplier.FilterSupplierRequest;
import com.bloom.app.api.dto.request.supplier.UpdateSupplierRequest;
import com.bloom.app.api.dto.response.supplier.SupplierResponse;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.SupplierService;
import com.bloom.app.service.mapper.SupplierMapper;
import com.bloom.app.service.specification.SupplierSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {
    private final SupplierRepository supplierRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierMapper supplierMapper;

    @Override
    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        String code = SupplierMapper.normalizeCode(request.getCode());
        log.debug("Creating supplier with code: {}", code);
        assertCodeAvailable(code);

        Supplier supplier = supplierMapper.createRequestToEntity(request);
        try {
            return supplierMapper.entityToResponse(supplierRepository.saveAndFlush(supplier));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SUPPLIER_CODE_ALREADY_EXISTS, code);
        }
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(String code, UpdateSupplierRequest request) {
        Supplier supplier = findSupplier(code);
        supplierMapper.copyUpdateRequestToEntity(request, supplier);
        return supplierMapper.entityToResponse(supplierRepository.saveAndFlush(supplier));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierResponse> filterSuppliers(FilterSupplierRequest request, Pageable pageable) {
        return supplierRepository.findAll(SupplierSpecification.filter(request), pageable)
            .map(supplierMapper::entityToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getSupplierDetails(String code) {
        return supplierMapper.entityToResponse(findSupplier(code));
    }

    @Override
    @Transactional
    public SupplierResponse setSupplierActive(String code, boolean active) {
        Supplier supplier = findSupplier(code);
        supplier.setActive(active);
        return supplierMapper.entityToResponse(supplierRepository.saveAndFlush(supplier));
    }

    @Override
    @Transactional
    public void deleteSupplier(String code) {
        Supplier supplier = findSupplier(code);
        if (goodsReceiptRepository.existsBySupplierId(supplier.getId())) {
            throw new BusinessException(ErrorCode.SUPPLIER_HAS_FINANCIAL_HISTORY, supplier.getCode());
        }
        try {
            supplierRepository.delete(supplier);
            supplierRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            // The database foreign key closes the race if history is posted after the pre-check.
            throw new BusinessException(ErrorCode.SUPPLIER_HAS_FINANCIAL_HISTORY, supplier.getCode());
        }
    }

    private Supplier findSupplier(String code) {
        String normalizedCode = SupplierMapper.normalizeCode(code);
        return supplierRepository.findByCode(normalizedCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND, normalizedCode));
    }

    private void assertCodeAvailable(String code) {
        if (supplierRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.SUPPLIER_CODE_ALREADY_EXISTS, code);
        }
    }
}

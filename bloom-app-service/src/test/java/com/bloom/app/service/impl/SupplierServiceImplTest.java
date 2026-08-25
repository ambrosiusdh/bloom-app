package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.supplier.CreateSupplierRequest;
import com.bloom.app.api.dto.request.supplier.FilterSupplierRequest;
import com.bloom.app.api.dto.request.supplier.UpdateSupplierRequest;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.mapper.SupplierMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierServiceImplTest {
    private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
    private final GoodsReceiptRepository goodsReceiptRepository = mock(GoodsReceiptRepository.class);
    private final SupplierMapper supplierMapper = Mappers.getMapper(SupplierMapper.class);
    private final SupplierServiceImpl service = new SupplierServiceImpl(
        supplierRepository,
        goodsReceiptRepository,
        supplierMapper
    );

    @Test
    void createNormalizesStableCodeAndMasterData() {
        CreateSupplierRequest request = CreateSupplierRequest.builder()
            .code(" sup-001 ")
            .name("  Bloom Textile  ")
            .contactNumber(" 021-555 ")
            .address("  Jakarta  ")
            .build();
        when(supplierRepository.saveAndFlush(any(Supplier.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createSupplier(request);

        assertThat(response.getCode()).isEqualTo("SUP-001");
        assertThat(response.getName()).isEqualTo("Bloom Textile");
        assertThat(response.getContactNumber()).isEqualTo("021-555");
        assertThat(response.getAddress()).isEqualTo("Jakarta");
        assertThat(response.isActive()).isTrue();
        verify(supplierRepository).existsByCode("SUP-001");
    }

    @Test
    void createRejectsCodeAlreadyOwnedByInactiveSupplier() {
        when(supplierRepository.existsByCode("SUP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createSupplier(CreateSupplierRequest.builder()
            .code("sup-001")
            .name("Replacement")
            .build()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUPPLIER_CODE_ALREADY_EXISTS));

        verify(supplierRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateChangesAllowedFieldsButKeepsCodeAndActivation() {
        Supplier supplier = supplier(10L, "SUP-001", true);
        UpdateSupplierRequest request = UpdateSupplierRequest.builder()
            .name(" New Name ")
            .contactNumber(" ")
            .address(" New address ")
            .build();
        when(supplierRepository.findByCode("SUP-001")).thenReturn(Optional.of(supplier));
        when(supplierRepository.saveAndFlush(supplier)).thenReturn(supplier);

        var response = service.updateSupplier(" sup-001 ", request);

        assertThat(response.getCode()).isEqualTo("SUP-001");
        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getContactNumber()).isNull();
        assertThat(response.getAddress()).isEqualTo("New address");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void activationSupportsDeactivationAndReactivationWithoutChangingCode() {
        Supplier supplier = supplier(10L, "SUP-001", true);
        when(supplierRepository.findByCode("SUP-001")).thenReturn(Optional.of(supplier));
        when(supplierRepository.saveAndFlush(supplier)).thenReturn(supplier);

        assertThat(service.setSupplierActive("sup-001", false).isActive()).isFalse();
        assertThat(service.setSupplierActive("SUP-001", true).isActive()).isTrue();
        assertThat(supplier.getCode()).isEqualTo("SUP-001");
    }

    @Test
    void deleteRejectsSupplierWithFinancialHistory() {
        Supplier supplier = supplier(10L, "SUP-001", false);
        when(supplierRepository.findByCode("SUP-001")).thenReturn(Optional.of(supplier));
        when(goodsReceiptRepository.existsBySupplierId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteSupplier("SUP-001"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUPPLIER_HAS_FINANCIAL_HISTORY));

        verify(supplierRepository, never()).delete(any(Supplier.class));
    }

    @Test
    void deleteRemovesOnlySupplierWithoutFinancialHistory() {
        Supplier supplier = supplier(10L, "SUP-001", false);
        when(supplierRepository.findByCode("SUP-001")).thenReturn(Optional.of(supplier));

        service.deleteSupplier("SUP-001");

        verify(goodsReceiptRepository).existsBySupplierId(10L);
        verify(supplierRepository).delete(supplier);
        verify(supplierRepository).flush();
    }

    @Test
    void listMapsRepositoryPageWithoutExposingEntities() {
        PageRequest pageable = PageRequest.of(0, 20);
        Supplier supplier = supplier(10L, "SUP-001", true);
        when(supplierRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(supplier), pageable, 1));

        var page = service.filterSuppliers(FilterSupplierRequest.builder().build(), pageable);

        assertThat(page.getContent()).singleElement().satisfies(response -> {
            assertThat(response.getCode()).isEqualTo("SUP-001");
            assertThat(response.isActive()).isTrue();
        });
    }

    private Supplier supplier(Long id, String code, boolean active) {
        return Supplier.builder()
            .id(id)
            .code(code)
            .name("Supplier")
            .active(active)
            .build();
    }
}

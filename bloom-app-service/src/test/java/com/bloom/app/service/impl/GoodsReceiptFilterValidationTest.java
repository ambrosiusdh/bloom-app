package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.domain.model.GoodsReceipt;
import com.bloom.app.domain.properties.BloomProperties;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.DocumentCounterService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.SupplierPaymentService;
import com.bloom.app.service.mapper.GoodsReceiptMapper;
import com.bloom.app.service.util.CurrentActorProvider;
import com.bloom.app.service.util.SupplierDebtCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoodsReceiptFilterValidationTest {
    @Test
    void invertedCalendarRangeFailsBeforeRepositoryAccess() {
        GoodsReceiptRepository repository = mock(GoodsReceiptRepository.class);
        GoodsReceiptServiceImpl service = new GoodsReceiptServiceImpl(
            repository,
            mock(CashSessionRepository.class),
            mock(ItemRepository.class),
            mock(StockMovementService.class),
            mock(DocumentCounterService.class),
            mock(GoodsReceiptMapper.class),
            mock(SupplierRepository.class),
            mock(CurrentActorProvider.class),
            mock(SupplierPaymentService.class),
            mock(SupplierDebtCalculator.class),
            new BloomProperties()
        );
        FilterGoodsReceiptRequest request = FilterGoodsReceiptRequest.builder()
            .receivedDateFrom(LocalDate.parse("2026-09-13"))
            .receivedDateTo(LocalDate.parse("2026-09-12"))
            .build();

        assertThatThrownBy(() -> service.filterGoodsReceipts(request, PageRequest.of(0, 10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Received date from must not be after received date to");
        verifyNoInteractions(repository);
    }

    @Test
    void overlongSupplierCodeFailsBeforeRepositoryAccess() {
        GoodsReceiptRepository repository = mock(GoodsReceiptRepository.class);
        GoodsReceiptServiceImpl service = service(
            repository, mock(GoodsReceiptMapper.class), mock(SupplierDebtCalculator.class));

        assertThatThrownBy(() -> service.filterGoodsReceipts(
            FilterGoodsReceiptRequest.builder().supplierCode("S".repeat(256)).build(),
            PageRequest.of(0, 10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Supplier code filter must not exceed 255 characters");

        verifyNoInteractions(repository);
    }

    @Test
    void pagedReadBulkLoadsResponsesAndPaymentsWhilePreservingRepositoryOrder() {
        GoodsReceiptRepository repository = mock(GoodsReceiptRepository.class);
        GoodsReceiptMapper mapper = mock(GoodsReceiptMapper.class);
        SupplierDebtCalculator debtCalculator = mock(SupplierDebtCalculator.class);
        GoodsReceiptServiceImpl service = service(repository, mapper, debtCalculator);
        PageRequest pageable = PageRequest.of(0, 2);
        GoodsReceipt second = GoodsReceipt.builder().id(2L).code("GR-2").build();
        GoodsReceipt first = GoodsReceipt.builder().id(1L).code("GR-1").build();

        when(repository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(second, first), pageable, 2));
        when(repository.findReadModelsByIdIn(List.of(2L, 1L)))
            .thenReturn(List.of(first, second));
        when(debtCalculator.validPaidAmounts(List.of(2L, 1L)))
            .thenReturn(Map.of(2L, new BigDecimal("4.0000")));
        when(mapper.toResponse(second)).thenReturn(
            GoodsReceiptResponse.builder().id(2L).code("GR-2").build());
        when(mapper.toResponse(first)).thenReturn(
            GoodsReceiptResponse.builder().id(1L).code("GR-1").build());
        when(debtCalculator.apply(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.filterGoodsReceipts(
            FilterGoodsReceiptRequest.builder().supplierCode(" sup-001 ").build(), pageable);

        assertThat(response.getContent()).extracting(GoodsReceiptResponse::getCode)
            .containsExactly("GR-2", "GR-1");
        verify(repository).findReadModelsByIdIn(List.of(2L, 1L));
        verify(debtCalculator).validPaidAmounts(List.of(2L, 1L));
    }

    private GoodsReceiptServiceImpl service(
            GoodsReceiptRepository repository,
            GoodsReceiptMapper mapper,
            SupplierDebtCalculator debtCalculator) {
        return new GoodsReceiptServiceImpl(
            repository,
            mock(CashSessionRepository.class),
            mock(ItemRepository.class),
            mock(StockMovementService.class),
            mock(DocumentCounterService.class),
            mapper,
            mock(SupplierRepository.class),
            mock(CurrentActorProvider.class),
            mock(SupplierPaymentService.class),
            debtCalculator,
            new BloomProperties()
        );
    }
}

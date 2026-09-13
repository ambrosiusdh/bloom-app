package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
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
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
}

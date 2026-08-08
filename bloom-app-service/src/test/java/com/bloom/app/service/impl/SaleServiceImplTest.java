package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.service.ExcelExportService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.SaleMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SaleServiceImplTest {
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final SaleMapper saleMapper = mock(SaleMapper.class);
    private final ExcelExportService excelExportService = mock(ExcelExportService.class);
    private final StockMovementService stockMovementService = mock(StockMovementService.class);
    private final DocumentCounterServiceImpl documentCounterService = mock(DocumentCounterServiceImpl.class);
    private final SaleServiceImpl service = new SaleServiceImpl(
        saleRepository,
        itemRepository,
        saleMapper,
        excelExportService,
        stockMovementService,
        documentCounterService
    );

    @Test
    void aggregatesDuplicateItemLocationLinesAndCapturesPriceSnapshots() {
        Item item = item("ITEM-1", "12.3456", "2.0000", "2.0000", true);
        CreateSaleRequest request = saleRequest(
            new BigDecimal("18.0184"),
            new BigDecimal("0.5000"),
            line("ITEM-1", "0.2500", StockLocation.STORE),
            line("ITEM-1", "0.7500", StockLocation.STORE),
            line("ITEM-1", "0.5000", StockLocation.WAREHOUSE)
        );
        Sale sale = Sale.builder().build();
        SaleResponse expectedResponse = SaleResponse.builder().build();

        when(saleMapper.createRequestToEntity(request)).thenReturn(sale);
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(Optional.of(item));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-1");
        when(saleRepository.save(sale)).thenAnswer(invocation -> {
            sale.setId(1L);
            return sale;
        });
        when(saleMapper.saleToResponse(sale)).thenReturn(expectedResponse);

        SaleResponse response = service.createSale(request);

        assertThat(response).isSameAs(expectedResponse);
        assertThat(sale.getItems()).hasSize(2);
        assertThat(sale.getItems().getFirst().getQuantity()).isEqualByComparingTo("1.0000");
        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("12.3456");
        assertThat(sale.getItems().get(0).getSubtotal()).isEqualByComparingTo("12.3456");
        assertThat(sale.getItems().get(1).getQuantity()).isEqualByComparingTo("0.5000");
        assertThat(sale.getItems().get(1).getSubtotal()).isEqualByComparingTo("6.1728");
        assertThat(sale.getSubtotalAmount()).isEqualByComparingTo("18.5184");
        assertThat(sale.getDiscountAmount()).isEqualByComparingTo("0.5000");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("18.0184");
        verify(itemRepository).findItemBySku("ITEM-1");
        verify(stockMovementService).recordSaleMovements(sale);
    }

    @Test
    void rejectsPaymentAgainstCalculatedTotalBeforePersisting() {
        Item item = item("ITEM-1", "10.0000", "5.0000", "0.0000", true);
        CreateSaleRequest request = saleRequest(
            new BigDecimal("19.9999"), BigDecimal.ZERO,
            line("ITEM-1", "2.0000", StockLocation.STORE));
        Sale sale = Sale.builder().build();

        when(saleMapper.createRequestToEntity(request)).thenReturn(sale);
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createSale(request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SALE_PAID_LESS_THAN_TOTAL));

        verify(saleRepository, never()).save(sale);
        verifyNoInteractions(stockMovementService);
    }

    @Test
    void checksStockAfterDuplicateLinesAreAggregated() {
        Item item = item("ITEM-1", "10.0000", "1.0000", "0.0000", true);
        CreateSaleRequest request = saleRequest(
            new BigDecimal("12.0000"), BigDecimal.ZERO,
            line("ITEM-1", "0.6000", StockLocation.STORE),
            line("ITEM-1", "0.6000", StockLocation.STORE));
        Sale sale = Sale.builder().build();

        when(saleMapper.createRequestToEntity(request)).thenReturn(sale);
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createSale(request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SALE_INSUFFICIENT_STOCK_STORE));

        verify(saleRepository, never()).save(sale);
        verifyNoInteractions(stockMovementService);
    }

    @Test
    void acceptsPositiveFractionalQuantityButRejectsFractionalQuantityForWholeItems() {
        Item fractionalItem = item("FRACTIONAL", "4.0000", "1.0000", "0.0000", true);
        Item wholeItem = item("WHOLE", "4.0000", "1.0000", "0.0000", false);
        Sale fractionalSale = Sale.builder().build();
        CreateSaleRequest fractionalRequest = saleRequest(
            new BigDecimal("1.0000"), BigDecimal.ZERO,
            line("FRACTIONAL", "0.2500", StockLocation.STORE));
        CreateSaleRequest wholeRequest = saleRequest(
            new BigDecimal("1.0000"), BigDecimal.ZERO,
            line("WHOLE", "0.2500", StockLocation.STORE));

        when(saleMapper.createRequestToEntity(fractionalRequest)).thenReturn(fractionalSale);
        when(itemRepository.findItemBySku("FRACTIONAL")).thenReturn(Optional.of(fractionalItem));
        when(itemRepository.findItemBySku("WHOLE")).thenReturn(Optional.of(wholeItem));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-1");
        when(saleRepository.save(fractionalSale)).thenAnswer(invocation -> {
            fractionalSale.setId(1L);
            return fractionalSale;
        });

        service.createSale(fractionalRequest);

        assertThat(fractionalSale.getItems().getFirst().getQuantity())
            .isEqualByComparingTo("0.2500");
        assertThatThrownBy(() -> service.createSale(wholeRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fractional quantity is not allowed for this item");
    }

    private CreateSaleRequest saleRequest(
            BigDecimal paidAmount,
            BigDecimal discountAmount,
            CreateSaleItemRequest... lines) {
        return CreateSaleRequest.builder()
            .paidAmount(paidAmount)
            .discountAmount(discountAmount)
            .paymentType(PaymentType.CASH)
            .saleItemList(List.of(lines))
            .build();
    }

    private CreateSaleItemRequest line(String sku, String quantity, StockLocation stockLocation) {
        return CreateSaleItemRequest.builder()
            .itemSku(sku)
            .quantity(new BigDecimal(quantity))
            .stockLocation(stockLocation)
            .build();
    }

    private Item item(
            String sku,
            String price,
            String storeStock,
            String warehouseStock,
            boolean fractionalQuantityAllowed) {
        return Item.builder()
            .id(42L)
            .name(sku)
            .sku(sku)
            .price(new BigDecimal(price))
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(fractionalQuantityAllowed)
            .stockStore(new BigDecimal(storeStock))
            .stockWarehouse(new BigDecimal(warehouseStock))
            .build();
    }
}

package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.response.sale.SaleCheckoutStatusResponse;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.CheckoutIdempotencyConflictException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.service.ExcelExportService;
import com.bloom.app.service.CashMovementService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.SaleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SaleServiceImplTest {
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final CashSessionRepository cashSessionRepository = mock(CashSessionRepository.class);
    private final SaleMapper saleMapper = mock(SaleMapper.class);
    private final ExcelExportService excelExportService = mock(ExcelExportService.class);
    private final StockMovementService stockMovementService = mock(StockMovementService.class);
    private final CashMovementService cashMovementService = mock(CashMovementService.class);
    private final DocumentCounterServiceImpl documentCounterService = mock(DocumentCounterServiceImpl.class);
    private final SaleServiceImpl service = new SaleServiceImpl(
        saleRepository,
        itemRepository,
        cashSessionRepository,
        saleMapper,
        excelExportService,
        stockMovementService,
        cashMovementService,
        documentCounterService
    );

    @BeforeEach
    void openCashSession() {
        when(cashSessionRepository.findFirstByStatusForUpdate(CashSessionStatus.OPEN))
            .thenReturn(Optional.of(CashSession.builder().id(7L).build()));
    }

    @Test
    void checkoutStatusNormalizesLocksThenReturnsCompletedSale() {
        Sale sale = Sale.builder().code("SALE-RECOVERED").build();
        SaleResponse original = SaleResponse.builder().code("SALE-RECOVERED").build();
        when(saleRepository.findByCheckoutIdempotencyKey("checkout-recovered"))
            .thenReturn(Optional.of(sale));
        when(saleMapper.saleToResponse(sale)).thenReturn(original);

        SaleCheckoutStatusResponse response =
            service.getCheckoutStatus("  checkout-recovered  ");

        assertThat(response.getStatus())
            .isEqualTo(SaleCheckoutStatusResponse.Status.COMPLETED);
        assertThat(response.getSale()).isSameAs(original);
        InOrder repositoryOrder = inOrder(saleRepository);
        repositoryOrder.verify(saleRepository).lockCheckoutKey("checkout-recovered");
        repositoryOrder.verify(saleRepository)
            .findByCheckoutIdempotencyKey("checkout-recovered");
        verifyNoInteractions(
            itemRepository,
            stockMovementService,
            cashMovementService,
            documentCounterService);
    }

    @Test
    void checkoutStatusReturnsUnknownWithoutMutation() {
        when(saleRepository.findByCheckoutIdempotencyKey("unused-checkout"))
            .thenReturn(Optional.empty());

        SaleCheckoutStatusResponse response =
            service.getCheckoutStatus("unused-checkout");

        assertThat(response.getStatus())
            .isEqualTo(SaleCheckoutStatusResponse.Status.UNKNOWN);
        assertThat(response.getSale()).isNull();
        InOrder repositoryOrder = inOrder(saleRepository);
        repositoryOrder.verify(saleRepository).lockCheckoutKey("unused-checkout");
        repositoryOrder.verify(saleRepository)
            .findByCheckoutIdempotencyKey("unused-checkout");
        verifyNoInteractions(
            itemRepository,
            saleMapper,
            stockMovementService,
            cashMovementService,
            documentCounterService);
    }

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
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-1"))).thenReturn(List.of(item));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-1");
        when(saleRepository.saveAndFlush(sale)).thenAnswer(invocation -> {
            sale.setId(1L);
            return sale;
        });
        when(saleMapper.saleToResponse(sale)).thenReturn(expectedResponse);

        SaleResponse response = service.createSale("checkout-aggregate", request);

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
        assertThat(sale.getPaidAmount()).isEqualByComparingTo("18.0184");
        assertThat(sale.getChangeAmount()).isEqualByComparingTo("0.0000");
        assertThat(sale.getCashSession().getId()).isEqualTo(7L);
        verify(itemRepository).findBySkuInOrderByIdForUpdate(List.of("ITEM-1"));
        verify(stockMovementService).recordSaleMovements(sale);
        verify(cashMovementService).recordMovement(argThat(command ->
            command.sessionId().equals(7L)
                && command.sourceId().equals(1L)
                && command.amount().compareTo(new BigDecimal("18.0184")) == 0));
    }

    @Test
    void cashUsesTenderedAmountForChangeButRecordsOnlySaleTotal() {
        Item item = item("ITEM-1", "10.0000", "2.0000", "0.0000", false);
        CreateSaleRequest request = saleRequest(
            new BigDecimal("20.0000"), BigDecimal.ZERO,
            line("ITEM-1", "1.0000", StockLocation.STORE));
        Sale sale = Sale.builder().build();

        when(saleMapper.createRequestToEntity(request)).thenReturn(sale);
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-1")))
            .thenReturn(List.of(item));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-CASH");
        when(saleRepository.saveAndFlush(sale)).thenAnswer(invocation -> {
            sale.setId(11L);
            return sale;
        });

        service.createSale("checkout-cash-change", request);

        assertThat(sale.getPaidAmount()).isEqualByComparingTo("20.0000");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("10.0000");
        assertThat(sale.getChangeAmount()).isEqualByComparingTo("10.0000");
        verify(cashMovementService).recordMovement(argThat(command ->
            command.amount().compareTo(new BigDecimal("10.0000")) == 0));
    }

    @Test
    void qrisRequiresExactPaymentAndNeverRecordsCashMovement() {
        Item item = item("ITEM-1", "10.0000", "2.0000", "0.0000", false);
        CreateSaleRequest exactRequest = saleRequest(
            new BigDecimal("10.0000"), BigDecimal.ZERO,
            line("ITEM-1", "1.0000", StockLocation.STORE));
        exactRequest.setPaymentType(PaymentType.QRIS);
        Sale sale = Sale.builder().build();

        when(saleMapper.createRequestToEntity(exactRequest)).thenReturn(sale);
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-1")))
            .thenReturn(List.of(item));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-QRIS");
        when(saleRepository.saveAndFlush(sale)).thenAnswer(invocation -> {
            sale.setId(12L);
            return sale;
        });

        service.createSale("checkout-qris", exactRequest);

        assertThat(sale.getChangeAmount()).isEqualByComparingTo("0.0000");
        verifyNoInteractions(cashMovementService);

        CreateSaleRequest overpaidRequest = saleRequest(
            new BigDecimal("11.0000"), BigDecimal.ZERO,
            line("ITEM-1", "1.0000", StockLocation.STORE));
        overpaidRequest.setPaymentType(PaymentType.QRIS);
        when(saleMapper.createRequestToEntity(overpaidRequest)).thenReturn(Sale.builder().build());

        assertThatThrownBy(() -> service.createSale("checkout-qris-overpaid", overpaidRequest))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.SALE_QRIS_PAYMENT_MISMATCH));
    }

    @Test
    void requiresOpenSessionBeforeLoadingOrMutatingInventory() {
        when(cashSessionRepository.findFirstByStatusForUpdate(CashSessionStatus.OPEN))
            .thenReturn(Optional.empty());
        CreateSaleRequest request = saleRequest(
            new BigDecimal("10.0000"), BigDecimal.ZERO,
            line("ITEM-1", "1.0000", StockLocation.STORE));

        assertThatThrownBy(() -> service.createSale("checkout-no-session", request))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessage("Checkout requires an open cash session");

        verifyNoInteractions(itemRepository, stockMovementService, cashMovementService);
    }

    @Test
    void retryReturnsOriginalSaleAndConflictingKeyReuseIsRejected() {
        Item item = item("ITEM-1", "10.0000", "2.0000", "0.0000", false);
        CreateSaleRequest request = saleRequest(
            new BigDecimal("10.0000"), BigDecimal.ZERO,
            line("ITEM-1", "1.0000", StockLocation.STORE));
        Sale sale = Sale.builder().build();
        SaleResponse response = SaleResponse.builder().code("SALE-RETRY").build();

        when(saleMapper.createRequestToEntity(request)).thenReturn(sale);
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-1")))
            .thenReturn(List.of(item));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-RETRY");
        when(saleRepository.saveAndFlush(sale)).thenAnswer(invocation -> {
            sale.setId(13L);
            return sale;
        });
        when(saleMapper.saleToResponse(sale)).thenReturn(response);

        assertThat(service.createSale("checkout-retry", request)).isSameAs(response);
        when(saleRepository.findByCheckoutIdempotencyKey("checkout-retry"))
            .thenReturn(Optional.of(sale));
        CreateSaleRequest equivalentScaleVariant = saleRequest(
            new BigDecimal("10.0"), new BigDecimal("0.000"),
            line("ITEM-1", "1.00", StockLocation.STORE));
        assertThat(service.createSale("checkout-retry", equivalentScaleVariant)).isSameAs(response);

        verify(saleRepository, times(1)).saveAndFlush(sale);
        verify(stockMovementService, times(1)).recordSaleMovements(sale);
        verify(cashMovementService, times(1)).recordMovement(argThat(command ->
            command.sourceId().equals(13L)));

        CreateSaleRequest changed = saleRequest(
            new BigDecimal("20.0000"), BigDecimal.ZERO,
            line("ITEM-1", "1.0000", StockLocation.STORE));
        assertThatThrownBy(() -> service.createSale("checkout-retry", changed))
            .isInstanceOf(CheckoutIdempotencyConflictException.class);
    }

    @Test
    void rejectsPaymentAgainstCalculatedTotalBeforePersisting() {
        Item item = item("ITEM-1", "10.0000", "5.0000", "0.0000", true);
        CreateSaleRequest request = saleRequest(
            new BigDecimal("19.9999"), BigDecimal.ZERO,
            line("ITEM-1", "2.0000", StockLocation.STORE));
        Sale sale = Sale.builder().build();

        when(saleMapper.createRequestToEntity(request)).thenReturn(sale);
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-1"))).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.createSale("checkout-underpaid", request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SALE_PAID_LESS_THAN_TOTAL));

        verify(saleRepository, never()).saveAndFlush(sale);
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
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-1"))).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.createSale("checkout-stock", request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SALE_INSUFFICIENT_STOCK_STORE));

        verify(saleRepository, never()).saveAndFlush(sale);
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
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("FRACTIONAL"))).thenReturn(List.of(fractionalItem));
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("WHOLE"))).thenReturn(List.of(wholeItem));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-1");
        when(saleRepository.saveAndFlush(fractionalSale)).thenAnswer(invocation -> {
            fractionalSale.setId(1L);
            return fractionalSale;
        });

        service.createSale("checkout-fractional", fractionalRequest);

        assertThat(fractionalSale.getItems().getFirst().getQuantity())
            .isEqualByComparingTo("0.2500");
        assertThatThrownBy(() -> service.createSale("checkout-whole", wholeRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fractional quantity is not allowed for this item");
    }

    @Test
    void delegatesDistinctItemLockOrderingToOneRepositoryQuery() {
        Item firstItem = item("ITEM-1", "2.0000", "1.0000", "0.0000", true);
        Item secondItem = item("ITEM-2", "3.0000", "1.0000", "0.0000", true);
        CreateSaleRequest request = saleRequest(
            new BigDecimal("5.0000"), BigDecimal.ZERO,
            line("ITEM-2", "1.0000", StockLocation.STORE),
            line("ITEM-1", "1.0000", StockLocation.STORE));
        Sale sale = Sale.builder().build();

        when(saleMapper.createRequestToEntity(request)).thenReturn(sale);
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-2", "ITEM-1")))
            .thenReturn(List.of(firstItem, secondItem));
        when(documentCounterService.generateNextCode(DocumentType.SALE)).thenReturn("SALE-1");
        when(saleRepository.saveAndFlush(sale)).thenAnswer(invocation -> {
            sale.setId(1L);
            return sale;
        });

        service.createSale("checkout-batch", request);

        assertThat(sale.getItems()).hasSize(2);
        verify(itemRepository).findBySkuInOrderByIdForUpdate(List.of("ITEM-2", "ITEM-1"));
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

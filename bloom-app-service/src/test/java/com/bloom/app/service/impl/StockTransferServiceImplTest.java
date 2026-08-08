package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stocktransfer.CreateStockTransferRequest;
import com.bloom.app.api.dto.request.stocktransfer.StockTransferLineRequest;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.IdempotencyConflictException;
import com.bloom.app.domain.exception.InsufficientStockException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockTransfer;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockTransferRepository;
import com.bloom.app.service.DocumentCounterService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.StockTransferMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockTransferServiceImplTest {
    private final StockTransferRepository stockTransferRepository = mock(StockTransferRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final StockTransferMapper stockTransferMapper = mock(StockTransferMapper.class);
    private final StockMovementService stockMovementService = mock(StockMovementService.class);
    private final DocumentCounterService documentCounterService = mock(DocumentCounterService.class);
    private final StockTransferServiceImpl service = new StockTransferServiceImpl(
        stockTransferRepository,
        itemRepository,
        stockTransferMapper,
        stockMovementService,
        documentCounterService
    );

    @Test
    void locksAllItemsAndRecordsOutThenInInAscendingItemIdOrder() {
        Item laterItem = item(20L, "ITEM-20", "5.0000", "0.0000", true);
        Item earlierItem = item(10L, "ITEM-10", "5.0000", "0.0000", true);
        CreateStockTransferRequest request = request(List.of(
            line(laterItem.getSku(), "0.5000", UnitOfMeasure.METER),
            line(earlierItem.getSku(), "0.2500", UnitOfMeasure.METER)
        ));
        StockTransfer transfer = mappedTransfer(request);
        StockTransferResponse expected = StockTransferResponse.builder().id(77L).build();

        when(stockTransferRepository.findByRequestKey("request-1")).thenReturn(Optional.empty());
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of("ITEM-10", "ITEM-20")))
            .thenReturn(List.of(earlierItem, laterItem));
        when(stockTransferMapper.createRequestToEntity(request)).thenReturn(transfer);
        when(documentCounterService.generateNextCode(DocumentType.STOCK_TRANSFER)).thenReturn("ST/VIII-2026/0001");
        when(stockTransferRepository.saveAndFlush(transfer)).thenAnswer(invocation -> {
            transfer.setId(77L);
            return transfer;
        });
        when(stockTransferMapper.toResponse(transfer)).thenReturn(expected);

        StockTransferResponse result = service.createStockTransfer(" request-1 ", request);

        assertThat(result).isSameAs(expected);
        assertThat(transfer.getLines()).extracting(line -> line.getItem().getId())
            .containsExactly(10L, 20L);
        verify(stockTransferRepository).lockRequestKey("request-1");
        InOrder movementOrder = inOrder(stockMovementService);
        verifyMovement(movementOrder, earlierItem, "0.2500", MovementType.OUT, StockLocation.STORE);
        verifyMovement(movementOrder, earlierItem, "0.2500", MovementType.IN, StockLocation.WAREHOUSE);
        verifyMovement(movementOrder, laterItem, "0.5000", MovementType.OUT, StockLocation.STORE);
        verifyMovement(movementOrder, laterItem, "0.5000", MovementType.IN, StockLocation.WAREHOUSE);
    }

    @Test
    void returnsExistingResultForSameRequestAndConflictsForChangedPayload() {
        Item item = item(10L, "ITEM-10", "5.0000", "0.0000", true);
        CreateStockTransferRequest original = request(List.of(
            line(item.getSku(), "1.0000", UnitOfMeasure.METER)));
        StockTransfer transfer = mappedTransfer(original);
        StockTransferResponse expected = StockTransferResponse.builder().id(77L).build();

        when(stockTransferRepository.findByRequestKey("retry-key"))
            .thenReturn(Optional.empty(), Optional.of(transfer), Optional.of(transfer));
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of(item.getSku())))
            .thenReturn(List.of(item));
        when(stockTransferMapper.createRequestToEntity(original)).thenReturn(transfer);
        when(documentCounterService.generateNextCode(DocumentType.STOCK_TRANSFER)).thenReturn("ST/VIII-2026/0001");
        when(stockTransferRepository.saveAndFlush(transfer)).thenAnswer(invocation -> {
            transfer.setId(77L);
            return transfer;
        });
        when(stockTransferMapper.toResponse(transfer)).thenReturn(expected);

        assertThat(service.createStockTransfer("retry-key", original)).isSameAs(expected);
        assertThat(service.createStockTransfer("retry-key", original)).isSameAs(expected);

        CreateStockTransferRequest changed = request(List.of(
            line(item.getSku(), "2.0000", UnitOfMeasure.METER)));
        assertThatThrownBy(() -> service.createStockTransfer("retry-key", changed))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessage("Idempotency key has already been used for a different stock transfer request");

        verify(documentCounterService, times(1)).generateNextCode(DocumentType.STOCK_TRANSFER);
        verify(stockMovementService, times(2)).recordMovement(
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateLinesBeforeTakingDatabaseLocks() {
        CreateStockTransferRequest request = request(List.of(
            line("ITEM-1", "1.0000", UnitOfMeasure.METER),
            line(" ITEM-1 ", "2.0000", UnitOfMeasure.METER)
        ));

        assertThatThrownBy(() -> service.createStockTransfer("request-1", request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Duplicate item lines are not allowed: ITEM-1");

        verifyNoInteractions(stockTransferRepository, itemRepository, stockMovementService);
    }

    @Test
    void rejectsUomMismatchAndFractionalQuantityForWholeUnitItem() {
        Item item = item(10L, "ITEM-10", "5.0000", "0.0000", false);
        CreateStockTransferRequest uomMismatch = request(List.of(
            line(item.getSku(), "1.0000", UnitOfMeasure.PIECE)));
        when(stockTransferRepository.findByRequestKey("uom-key")).thenReturn(Optional.empty());
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of(item.getSku())))
            .thenReturn(List.of(item));

        assertThatThrownBy(() -> service.createStockTransfer("uom-key", uomMismatch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unit of measure must match base unit METER");

        CreateStockTransferRequest fractional = request(List.of(
            line(item.getSku(), "0.5000", UnitOfMeasure.METER)));
        when(stockTransferRepository.findByRequestKey("fraction-key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createStockTransfer("fraction-key", fractional))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fractional quantity is not allowed for this item");

        verify(stockTransferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stockMovementService);
    }

    @Test
    void validatesAvailabilityBeforePersistingAnyTransferData() {
        Item item = item(10L, "ITEM-10", "0.2500", "10.0000", true);
        CreateStockTransferRequest request = request(List.of(
            line(item.getSku(), "0.2501", UnitOfMeasure.METER)));
        when(stockTransferRepository.findByRequestKey("stock-key")).thenReturn(Optional.empty());
        when(itemRepository.findBySkuInOrderByIdForUpdate(List.of(item.getSku())))
            .thenReturn(List.of(item));

        assertThatThrownBy(() -> service.createStockTransfer("stock-key", request))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessageContaining("STORE");

        verify(stockTransferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stockMovementService);
    }

    private CreateStockTransferRequest request(List<StockTransferLineRequest> lines) {
        return CreateStockTransferRequest.builder()
            .sourceLocation(StockLocation.STORE)
            .destinationLocation(StockLocation.WAREHOUSE)
            .description("Replenishment")
            .lines(lines)
            .build();
    }

    private StockTransferLineRequest line(String sku, String quantity, UnitOfMeasure unit) {
        return StockTransferLineRequest.builder()
            .itemSku(sku)
            .quantity(new BigDecimal(quantity))
            .unitOfMeasure(unit)
            .build();
    }

    private Item item(
            Long id, String sku, String storeStock, String warehouseStock,
            boolean fractionalQuantityAllowed) {
        return Item.builder()
            .id(id)
            .sku(sku)
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(fractionalQuantityAllowed)
            .stockStore(new BigDecimal(storeStock))
            .stockWarehouse(new BigDecimal(warehouseStock))
            .build();
    }

    private StockTransfer mappedTransfer(CreateStockTransferRequest request) {
        return StockTransfer.builder()
            .sourceLocation(request.getSourceLocation())
            .destinationLocation(request.getDestinationLocation())
            .description(request.getDescription())
            .build();
    }

    private void verifyMovement(
            InOrder order, Item item, String quantity, MovementType type, StockLocation location) {
        order.verify(stockMovementService).recordMovement(
            MovementSourceType.TRANSFER,
            77L,
            item,
            new BigDecimal(quantity),
            type,
            "ST/VIII-2026/0001",
            location
        );
    }
}

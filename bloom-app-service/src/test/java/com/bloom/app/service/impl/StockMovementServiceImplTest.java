package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockMovement;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.domain.model.StockAdjustment;
import com.bloom.app.domain.model.StockAdjustmentItem;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.exception.BaseUnitOfMeasureImmutableException;
import com.bloom.app.domain.exception.InsufficientStockException;
import com.bloom.app.domain.exception.StockConcurrencyException;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockMovementServiceImplTest {
    private final StockMovementRepository stockMovementRepository = mock(StockMovementRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final StockMovementServiceImpl service = new StockMovementServiceImpl(
        stockMovementRepository, itemRepository);

    @Test
    void recordsFractionalMovementWithoutRounding() {
        Item item = item(true, "1.2500");
        when(itemRepository.saveAndFlush(item)).thenReturn(item);

        service.recordMovement(
            MovementSourceType.SALE, 1L, item, new BigDecimal("0.2500"),
            MovementType.OUT, "SALE-1", StockLocation.STORE);

        assertThat(item.getStockStore()).isEqualByComparingTo("1.0000");
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).saveAndFlush(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getQuantity()).isEqualByComparingTo("0.2500");
        assertThat(movementCaptor.getValue().getQtyBefore()).isEqualByComparingTo("1.2500");
        assertThat(movementCaptor.getValue().getQtyAfter()).isEqualByComparingTo("1.0000");
        assertThat(movementCaptor.getValue().getReferenceNo()).isEqualTo("SALE-1");
        assertThat(movementCaptor.getValue().getQtyAfter()).isEqualByComparingTo(
            movementCaptor.getValue().getQtyBefore().subtract(movementCaptor.getValue().getQuantity()));
    }

    @Test
    void rejectsFractionalMovementForNonFractionalItem() {
        Item item = item(false, "5.0000");

        assertThatThrownBy(() -> service.recordMovement(
            MovementSourceType.SALE, 1L, item, new BigDecimal("0.5000"),
            MovementType.OUT, "SALE-1", StockLocation.STORE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fractional quantity is not allowed for this item");

        verifyNoInteractions(itemRepository, stockMovementRepository);
    }

    @Test
    void rejectsMovementThatWouldMakeStockNegative() {
        Item item = item(true, "0.2500");

        assertThatThrownBy(() -> service.recordMovement(
            MovementSourceType.SALE, 1L, item, new BigDecimal("0.2501"),
            MovementType.OUT, "SALE-1", StockLocation.STORE))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessageContaining("Insufficient stock");

        verifyNoInteractions(itemRepository, stockMovementRepository);
    }

    @Test
    void translatesOptimisticLockFailureToStockDomainConflict() {
        Item item = item(true, "1.0000");
        when(itemRepository.saveAndFlush(item)).thenThrow(
            new ObjectOptimisticLockingFailureException(Item.class, item.getId()));

        assertThatThrownBy(() -> service.recordMovement(
            MovementSourceType.SALE, 1L, item, new BigDecimal("0.2500"),
            MovementType.OUT, "SALE-1", StockLocation.STORE))
            .isInstanceOf(StockConcurrencyException.class)
            .hasMessageContaining("modified concurrently");

        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void recordsAggregatedSaleDeductionOnlyOnceWhenReplayed() {
        Item item = item(true, "2.0000");
        Sale sale = Sale.builder()
            .id(7L)
            .code("SALE-7")
            .items(List.of(
                SaleItem.builder().item(item).quantity(new BigDecimal("0.2500"))
                    .stockLocation(StockLocation.STORE).build(),
                SaleItem.builder().item(item).quantity(new BigDecimal("0.5000"))
                    .stockLocation(StockLocation.STORE).build()
            ))
            .build();
        when(stockMovementRepository.existsBySourceTypeAndSourceIdAndProduct_IdAndStockLocation(
            MovementSourceType.SALE, 7L, item.getId(), StockLocation.STORE))
            .thenReturn(false, true);
        when(itemRepository.saveAndFlush(item)).thenReturn(item);

        service.recordSaleMovements(sale);
        service.recordSaleMovements(sale);

        assertThat(item.getStockStore()).isEqualByComparingTo("1.2500");
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).saveAndFlush(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getMovementType()).isEqualTo(MovementType.OUT);
        assertThat(movementCaptor.getValue().getQuantity()).isEqualByComparingTo("0.7500");
    }

    @Test
    void derivesCorrectionMovementDirectionAndMagnitudeFromSnapshots() {
        Item item = item(true, "5.0000");
        StockAdjustmentItem adjustmentItem = StockAdjustmentItem.builder()
            .item(item)
            .actionType(StockAdjustmentActionType.CORRECTION)
            .changeQuantity(new BigDecimal("1.2500"))
            .previousStock(new BigDecimal("5.0000"))
            .newStock(new BigDecimal("1.2500"))
            .stockLocation(StockLocation.STORE)
            .build();
        StockAdjustment adjustment = StockAdjustment.builder()
            .id(8L)
            .stockAdjustmentCode("SA-8")
            .items(List.of(adjustmentItem))
            .build();
        when(itemRepository.saveAndFlush(item)).thenReturn(item);

        service.recordManualAdjustment(adjustment);

        assertThat(item.getStockStore()).isEqualByComparingTo("1.2500");
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).saveAndFlush(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getMovementType()).isEqualTo(MovementType.OUT);
        assertThat(movementCaptor.getValue().getQuantity()).isEqualByComparingTo("3.7500");
        assertThat(movementCaptor.getValue().getQtyBefore()).isEqualByComparingTo("5.0000");
        assertThat(movementCaptor.getValue().getQtyAfter()).isEqualByComparingTo("1.2500");
    }

    @Test
    void preventsBaseUomChangeAfterFirstMovement() {
        Item item = item(true, "1.0000");
        when(stockMovementRepository.existsByProductId(item.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.validateBaseUnitOfMeasureChange(item, UnitOfMeasure.LITER))
            .isInstanceOf(BaseUnitOfMeasureImmutableException.class)
            .hasMessageContaining("cannot change");
    }

    @Test
    void allowsBaseUomChangeBeforeFirstMovement() {
        Item item = item(true, "0.0000");
        when(stockMovementRepository.existsByProductId(item.getId())).thenReturn(false);

        service.validateBaseUnitOfMeasureChange(item, UnitOfMeasure.LITER);

        verify(stockMovementRepository).existsByProductId(item.getId());
    }

    private Item item(boolean fractionalQuantityAllowed, String storeStock) {
        return Item.builder()
            .id(42L)
            .sku("ITEM-1")
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(fractionalQuantityAllowed)
            .stockStore(new BigDecimal(storeStock))
            .stockWarehouse(BigDecimal.ZERO)
            .build();
    }
}

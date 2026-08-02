package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockMovement;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemAuditLogRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StockMovementServiceImplTest {
    private final StockMovementRepository stockMovementRepository = mock(StockMovementRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final ItemAuditLogRepository itemAuditLogRepository = mock(ItemAuditLogRepository.class);
    private final StockMovementServiceImpl service = new StockMovementServiceImpl(
        stockMovementRepository, itemRepository, itemAuditLogRepository);

    @Test
    void recordsFractionalMovementWithoutRounding() {
        Item item = item(true, "1.2500");

        service.recordMovement(
            MovementSourceType.SALE, 1L, item, new BigDecimal("0.2500"),
            MovementType.OUT, "SALE-1", StockLocation.STORE);

        assertThat(item.getStockStore()).isEqualByComparingTo("1.0000");
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getQuantity()).isEqualByComparingTo("0.2500");
        assertThat(movementCaptor.getValue().getQtyBefore()).isEqualByComparingTo("1.2500");
        assertThat(movementCaptor.getValue().getQtyAfter()).isEqualByComparingTo("1.0000");
    }

    @Test
    void rejectsFractionalMovementForNonFractionalItem() {
        Item item = item(false, "5.0000");

        assertThatThrownBy(() -> service.recordMovement(
            MovementSourceType.SALE, 1L, item, new BigDecimal("0.5000"),
            MovementType.OUT, "SALE-1", StockLocation.STORE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fractional quantity is not allowed for this item");

        verifyNoInteractions(itemRepository, stockMovementRepository, itemAuditLogRepository);
    }

    @Test
    void rejectsMovementThatWouldMakeStockNegative() {
        Item item = item(true, "0.2500");

        assertThatThrownBy(() -> service.recordMovement(
            MovementSourceType.SALE, 1L, item, new BigDecimal("0.2501"),
            MovementType.OUT, "SALE-1", StockLocation.STORE))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Insufficient stock");

        verifyNoInteractions(itemRepository, stockMovementRepository, itemAuditLogRepository);
    }

    private Item item(boolean fractionalQuantityAllowed, String storeStock) {
        return Item.builder()
            .sku("ITEM-1")
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(fractionalQuantityAllowed)
            .stockStore(new BigDecimal(storeStock))
            .stockWarehouse(BigDecimal.ZERO)
            .build();
    }
}

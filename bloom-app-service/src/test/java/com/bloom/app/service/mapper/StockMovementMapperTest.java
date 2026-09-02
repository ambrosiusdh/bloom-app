package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.domain.model.StockMovement;
import com.bloom.app.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementMapperTest {
    private StockMovementMapperImpl mapper;

    @BeforeEach
    void setUp() {
        ItemMapperImpl itemMapper = new ItemMapperImpl();
        ReflectionTestUtils.setField(
            itemMapper, "itemCategoryMapper", Mappers.getMapper(ItemCategoryMapper.class));
        mapper = new StockMovementMapperImpl();
        ReflectionTestUtils.setField(mapper, "itemMapper", itemMapper);
    }

    @Test
    void mapsAuthoritativeLedgerWithoutLosingReferenceData() {
        Instant createdAt = Instant.parse("2026-08-11T12:00:00Z");
        Item item = Item.builder()
            .id(7L)
            .name("Cotton")
            .sku("SKU-7")
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .stockStore(new BigDecimal("3.0000"))
            .stockWarehouse(BigDecimal.ZERO)
            .category(ItemCategory.builder().id(1L).name("Fabric").code("FAB").build())
            .build();
        StockMovement movement = StockMovement.builder()
            .id(42L)
            .product(item)
            .sourceType(MovementSourceType.STOCK_ADJUSTMENT)
            .sourceId(99L)
            .movementType(MovementType.IN)
            .stockLocation(StockLocation.WAREHOUSE)
            .quantity(new BigDecimal("1.2500"))
            .qtyBefore(new BigDecimal("0.5000"))
            .qtyAfter(new BigDecimal("1.7500"))
            .referenceNo("SA-0099")
            .adjustmentActionType(StockAdjustmentActionType.ADD)
            .createdBy("cashier")
            .createdAt(createdAt)
            .build();

        StockMovementResponse response = mapper.toResponse(movement);

        assertThat(response.getItem().getSku()).isEqualTo("SKU-7");
        assertThat(response.getLocation()).isEqualTo(StockLocation.WAREHOUSE);
        assertThat(response.getReferenceNo()).isEqualTo("SA-0099");
        assertThat(response.getAdjustmentActionType()).isEqualTo(StockAdjustmentActionType.ADD);

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getSourceType()).isEqualTo(MovementSourceType.STOCK_ADJUSTMENT);
        assertThat(response.getQuantity()).isEqualByComparingTo("1.2500");
        assertThat(response.getQtyBefore()).isEqualByComparingTo("0.5000");
        assertThat(response.getQtyAfter()).isEqualByComparingTo("1.7500");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }
}

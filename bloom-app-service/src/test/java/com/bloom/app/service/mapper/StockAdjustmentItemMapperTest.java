package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentItemResponse;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockAdjustmentItem;
import com.bloom.app.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockAdjustmentItemMapperTest {
    private StockAdjustmentItemMapperImpl mapper;

    @BeforeEach
    void setUp() {
        ItemMapperImpl itemMapper = new ItemMapperImpl();
        ReflectionTestUtils.setField(
            itemMapper, "itemCategoryMapper", Mappers.getMapper(ItemCategoryMapper.class));
        mapper = new StockAdjustmentItemMapperImpl();
        ReflectionTestUtils.setField(mapper, "itemMapper", itemMapper);
    }

    @Test
    void mapsLocationAndAuthoritativeBalanceSnapshots() {
        Item item = Item.builder()
            .id(7L)
            .sku("KAIN-001")
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .stockStore(new BigDecimal("4.7500"))
            .stockWarehouse(BigDecimal.ZERO)
            .build();
        StockAdjustmentItem adjustmentItem = StockAdjustmentItem.builder()
            .id(81L)
            .item(item)
            .actionType(StockAdjustmentActionType.REMOVE)
            .stockLocation(StockLocation.STORE)
            .changeQuantity(new BigDecimal("0.2500"))
            .previousStock(new BigDecimal("5.0000"))
            .newStock(new BigDecimal("4.7500"))
            .build();

        StockAdjustmentItemResponse response = mapper.toResponse(adjustmentItem);

        assertThat(response.getItem().getSku()).isEqualTo("KAIN-001");
        assertThat(response.getStockLocation()).isEqualTo(StockLocation.STORE);
        assertThat(response.getChangeQuantity()).isEqualByComparingTo("0.2500");
        assertThat(response.getPreviousStock()).isEqualByComparingTo("5.0000");
        assertThat(response.getNewStock()).isEqualByComparingTo("4.7500");
    }
}

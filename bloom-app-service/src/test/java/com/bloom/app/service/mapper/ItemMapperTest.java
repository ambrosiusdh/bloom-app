package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMapperTest {
    private final ItemMapperImpl mapper = new ItemMapperImpl();

    ItemMapperTest() {
        ReflectionTestUtils.setField(mapper, "itemCategoryMapper", Mappers.getMapper(ItemCategoryMapper.class));
    }

    @Test
    void mapsFrontendInventoryReadModelWithoutMergingLocationStock() {
        CreateItemRequest request = CreateItemRequest.builder()
            .name("Fabric")
            .categoryCode("FAB")
            .price(BigDecimal.TEN)
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .stockStore(new BigDecimal("1.2500"))
            .stockWarehouse(new BigDecimal("0.0001"))
            .build();

        Item item = mapper.createRequestToEntity(request);
        assertThat(item.getStockStore()).isEqualByComparingTo("0");
        assertThat(item.getStockWarehouse()).isEqualByComparingTo("0");
        Item stockedItem = Item.builder()
            .baseUnitOfMeasure(item.getBaseUnitOfMeasure())
            .fractionalQuantityAllowed(item.isFractionalQuantityAllowed())
            .stockStore(new BigDecimal("1.2500"))
            .stockWarehouse(new BigDecimal("0.0001"))
            .active(false)
            .build();

        ItemResponse response = mapper.itemToItemResponse(stockedItem, true);

        assertThat(item.getBaseUnitOfMeasure()).isEqualTo(UnitOfMeasure.METER);
        assertThat(item.isFractionalQuantityAllowed()).isTrue();
        assertThat(response.getBaseUnitOfMeasure()).isEqualTo(UnitOfMeasure.METER);
        assertThat(response.isFractionalQuantityAllowed()).isTrue();
        assertThat(response.getStockStore()).isEqualTo(new BigDecimal("1.2500"));
        assertThat(response.getStockWarehouse()).isEqualTo(new BigDecimal("0.0001"));
        assertThat(response.isActive()).isFalse();
        assertThat(response.isHasStockMovements()).isTrue();
        assertThat(response.isBaseUnitOfMeasureLocked()).isTrue();
        assertThat(response.isFractionalQuantityAllowedLocked()).isTrue();

    }

    @Test
    void copiesActiveStateAndLeavesLocksOpenWithoutMovements() {
        Item item = Item.builder()
            .active(true)
            .stockStore(BigDecimal.ZERO)
            .stockWarehouse(BigDecimal.ZERO)
            .build();

        ItemResponse response = mapper.itemToItemResponse(item, false);

        assertThat(response.isActive()).isTrue();
        assertThat(response.isHasStockMovements()).isFalse();
        assertThat(response.isBaseUnitOfMeasureLocked()).isFalse();
        assertThat(response.isFractionalQuantityAllowedLocked()).isFalse();
    }
}

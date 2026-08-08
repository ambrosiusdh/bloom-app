package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMapperTest {
    private final ItemMapperImpl mapper = new ItemMapperImpl();

    ItemMapperTest() {
        ReflectionTestUtils.setField(mapper, "itemCategoryMapper", Mappers.getMapper(ItemCategoryMapper.class));
    }

    @Test
    void mapsUomMetadataInBothDirections() {
        CreateItemRequest request = CreateItemRequest.builder()
            .name("Fabric")
            .categoryCode("FAB")
            .price(java.math.BigDecimal.TEN)
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .stockStore(new java.math.BigDecimal("1.2500"))
            .stockWarehouse(new java.math.BigDecimal("0.5000"))
            .build();

        Item item = mapper.createRequestToEntity(request);
        assertThat(item.getStockStore()).isEqualByComparingTo("0");
        assertThat(item.getStockWarehouse()).isEqualByComparingTo("0");
        Item stockedItem = Item.builder()
            .baseUnitOfMeasure(item.getBaseUnitOfMeasure())
            .fractionalQuantityAllowed(item.isFractionalQuantityAllowed())
            .stockStore(new java.math.BigDecimal("1.2500"))
            .stockWarehouse(new java.math.BigDecimal("0.5000"))
            .build();
        ItemResponse response = mapper.itemToItemResponse(stockedItem);

        assertThat(item.getBaseUnitOfMeasure()).isEqualTo(UnitOfMeasure.METER);
        assertThat(item.isFractionalQuantityAllowed()).isTrue();
        assertThat(response.getBaseUnitOfMeasure()).isEqualTo(UnitOfMeasure.METER);
        assertThat(response.isFractionalQuantityAllowed()).isTrue();
        assertThat(response.getStockStore()).isEqualByComparingTo("1.2500");
        assertThat(response.getStockWarehouse()).isEqualByComparingTo("0.5000");
        assertThat(response.getStockQuantity()).isEqualByComparingTo("1.7500");
    }
}

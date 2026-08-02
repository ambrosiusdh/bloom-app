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
            .build();

        Item item = mapper.createRequestToEntity(request);
        ItemResponse response = mapper.itemToItemResponse(item);

        assertThat(item.getBaseUnitOfMeasure()).isEqualTo(UnitOfMeasure.METER);
        assertThat(item.isFractionalQuantityAllowed()).isTrue();
        assertThat(response.getBaseUnitOfMeasure()).isEqualTo(UnitOfMeasure.METER);
        assertThat(response.isFractionalQuantityAllowed()).isTrue();
    }
}

package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
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
    void mapsLedgerAndLegacyAuditShapesWithoutLosingReferenceData() {
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
            .legacyAuditLogId(88L)
            .product(item)
            .sourceType(MovementSourceType.GOODS_RECEIPT)
            .sourceId(99L)
            .movementType(MovementType.IN)
            .stockLocation(StockLocation.WAREHOUSE)
            .quantity(new BigDecimal("1.2500"))
            .qtyBefore(new BigDecimal("0.5000"))
            .qtyAfter(new BigDecimal("1.7500"))
            .referenceNo("GR-0099")
            .displayReference("GR-0099")
            .effectiveAdjustmentActionType(StockAdjustmentActionType.ADD)
            .createdBy("cashier")
            .createdAt(createdAt)
            .build();

        StockMovementResponse response = mapper.toResponse(movement);
        ItemAuditLogResponse legacy = mapper.toAuditResponse(response);

        assertThat(response.getItem().getSku()).isEqualTo("SKU-7");
        assertThat(response.getLocation()).isEqualTo(StockLocation.WAREHOUSE);
        assertThat(response.getReferenceNo()).isEqualTo("GR-0099");
        assertThat(response.getAdjustmentActionType()).isEqualTo(StockAdjustmentActionType.ADD);
        assertThat(legacy.getId()).isEqualTo(88L);
        assertThat(legacy.getSource()).isEqualTo(MovementSourceType.GOODS_RECEIPT);
        assertThat(legacy.getQty()).isEqualByComparingTo("1.2500");
        assertThat(legacy.getReferenceNo()).isEqualTo("GR-0099");
        assertThat(legacy.getCreatedDate()).isEqualTo(createdAt);
    }
}

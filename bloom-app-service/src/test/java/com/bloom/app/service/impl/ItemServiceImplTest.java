package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemCategoryCounterRepository;
import com.bloom.app.persistence.repository.ItemCategoryRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.ItemMapper;
import com.bloom.app.service.util.PdfGeneratorUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemServiceImplTest {
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final ItemCategoryRepository itemCategoryRepository = mock(ItemCategoryRepository.class);
    private final ItemCategoryCounterRepository itemCategoryCounterRepository =
        mock(ItemCategoryCounterRepository.class);
    private final ItemMapper itemMapper = mock(ItemMapper.class);
    private final PdfGeneratorUtil pdfGeneratorUtil = mock(PdfGeneratorUtil.class);
    private final StockMovementService stockMovementService = mock(StockMovementService.class);
    private final ItemServiceImpl service = new ItemServiceImpl(
        itemRepository,
        itemCategoryRepository,
        itemCategoryCounterRepository,
        itemMapper,
        pdfGeneratorUtil,
        stockMovementService
    );

    @Test
    void recordsNonzeroOpeningBalancesAsLedgerMovements() {
        CreateItemRequest request = CreateItemRequest.builder()
            .name("Fabric")
            .categoryCode("FAB")
            .sku("FAB-00001")
            .price(BigDecimal.TEN)
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .stockStore(new BigDecimal("1.2500"))
            .stockWarehouse(new BigDecimal("2.0000"))
            .build();
        ItemCategory category = ItemCategory.builder().code("FAB").build();
        Item item = Item.builder()
            .stockStore(BigDecimal.ZERO)
            .stockWarehouse(BigDecimal.ZERO)
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .build();
        ItemResponse expectedResponse = ItemResponse.builder().sku("FAB-00001").build();

        when(itemCategoryRepository.findByCode("FAB")).thenReturn(Optional.of(category));
        when(itemMapper.createRequestToEntity(request)).thenReturn(item);
        when(itemRepository.save(item)).thenAnswer(invocation -> {
            Item savedItem = invocation.getArgument(0);
            savedItem.setId(42L);
            return savedItem;
        });
        when(itemMapper.itemToItemResponse(item)).thenReturn(expectedResponse);

        ItemResponse response = service.createItem(request);

        assertThat(response).isSameAs(expectedResponse);
        verify(stockMovementService).recordMovement(
            eq(MovementSourceType.OPENING_BALANCE),
            eq(42L),
            same(item),
            argThat(quantity -> quantity.compareTo(new BigDecimal("1.2500")) == 0),
            eq(MovementType.IN),
            eq("FAB-00001"),
            eq(StockLocation.STORE)
        );
        verify(stockMovementService).recordMovement(
            eq(MovementSourceType.OPENING_BALANCE),
            eq(42L),
            same(item),
            argThat(quantity -> quantity.compareTo(new BigDecimal("2.0000")) == 0),
            eq(MovementType.IN),
            eq("FAB-00001"),
            eq(StockLocation.WAREHOUSE)
        );
    }
}

package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.request.item.FilterItemRequest;
import com.bloom.app.api.dto.request.item.UpdateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.BaseUnitOfMeasureImmutableException;
import com.bloom.app.domain.exception.FractionalQuantityPolicyImmutableException;
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
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        Item item = item(42L, true, true);
        item.setId(null);
        ItemResponse expectedResponse = ItemResponse.builder()
            .sku("FAB-00001")
            .hasStockMovements(true)
            .build();

        when(itemCategoryRepository.findByCode("FAB")).thenReturn(Optional.of(category));
        when(itemMapper.createRequestToEntity(request)).thenReturn(item);
        when(itemRepository.saveAndFlush(item)).thenAnswer(invocation -> {
            Item savedItem = invocation.getArgument(0);
            assertThat(savedItem.getStockStore()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(savedItem.getStockWarehouse()).isEqualByComparingTo(BigDecimal.ZERO);
            savedItem.setId(42L);
            return savedItem;
        });
        when(itemMapper.itemToItemResponse(item, true)).thenReturn(expectedResponse);

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
        InOrder persistenceThenMovements = inOrder(itemRepository, stockMovementService);
        persistenceThenMovements.verify(itemRepository).saveAndFlush(item);
        persistenceThenMovements.verify(stockMovementService).recordMovement(
            eq(MovementSourceType.OPENING_BALANCE), eq(42L), same(item),
            argThat(quantity -> quantity.compareTo(new BigDecimal("1.2500")) == 0),
            eq(MovementType.IN), eq("FAB-00001"), eq(StockLocation.STORE));
    }

    @Test
    void acceptsOmittedOpeningBalancesWithoutMovements() {
        CreateItemRequest request = CreateItemRequest.builder()
            .name("Fabric")
            .categoryCode("FAB")
            .sku("FAB-00002")
            .price(BigDecimal.TEN)
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .stockStore(null)
            .stockWarehouse(null)
            .build();
        ItemCategory category = ItemCategory.builder().code("FAB").build();
        Item item = item(null, true, true);

        when(itemCategoryRepository.findByCode("FAB")).thenReturn(Optional.of(category));
        when(itemMapper.createRequestToEntity(request)).thenReturn(item);
        when(itemRepository.saveAndFlush(item)).thenReturn(item);
        when(itemMapper.itemToItemResponse(item, false)).thenReturn(ItemResponse.builder().build());

        service.createItem(request);

        verify(itemRepository).saveAndFlush(item);
        verify(stockMovementService, never()).recordMovement(
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listUsesOneBatchMovementLookupAndMapsBothLockStates() {
        FilterItemRequest filter = FilterItemRequest.builder().build();
        PageRequest pageable = PageRequest.of(0, 20);
        Item withoutMovements = item(1L, true, true);
        Item withMovements = item(2L, true, true);
        ItemResponse unlocked = ItemResponse.builder().sku("ITEM-1").hasStockMovements(false).build();
        ItemResponse locked = ItemResponse.builder().sku("ITEM-2").hasStockMovements(true).build();

        when(itemRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(withoutMovements, withMovements), pageable, 2));
        when(stockMovementService.findItemIdsWithStockMovements(List.of(1L, 2L)))
            .thenReturn(Set.of(2L));
        when(itemMapper.itemToItemResponse(withoutMovements, false)).thenReturn(unlocked);
        when(itemMapper.itemToItemResponse(withMovements, true)).thenReturn(locked);

        var result = service.filterItems(filter, pageable);

        assertThat(result.getContent()).containsExactly(unlocked, locked);
        verify(stockMovementService).findItemIdsWithStockMovements(List.of(1L, 2L));
        verify(stockMovementService, never()).hasStockMovements(any());
        verify(itemMapper).itemToItemResponse(withoutMovements, false);
        verify(itemMapper).itemToItemResponse(withMovements, true);
    }

    @Test
    void detailWithoutMovementsExposesActiveAndUnlockedState() {
        Item item = item(1L, true, true);
        ItemResponse expected = ItemResponse.builder()
            .active(true)
            .hasStockMovements(false)
            .baseUnitOfMeasureLocked(false)
            .fractionalQuantityAllowedLocked(false)
            .build();
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(Optional.of(item));
        when(stockMovementService.hasStockMovements(1L)).thenReturn(false);
        when(itemMapper.itemToItemResponse(item, false)).thenReturn(expected);

        ItemResponse response = service.getItemDetails("ITEM-1");

        assertThat(response).isSameAs(expected);
        assertThat(response.isActive()).isTrue();
        assertThat(response.isBaseUnitOfMeasureLocked()).isFalse();
    }

    @Test
    void detailWithMovementsExposesInactiveAndLockedState() {
        Item item = item(2L, false, true);
        item.setSku("ITEM-2");
        ItemResponse expected = ItemResponse.builder()
            .active(false)
            .hasStockMovements(true)
            .baseUnitOfMeasureLocked(true)
            .fractionalQuantityAllowedLocked(true)
            .build();
        when(itemRepository.findItemBySku("ITEM-2")).thenReturn(Optional.of(item));
        when(stockMovementService.hasStockMovements(2L)).thenReturn(true);
        when(itemMapper.itemToItemResponse(item, true)).thenReturn(expected);

        ItemResponse response = service.getItemDetails("ITEM-2");

        assertThat(response).isSameAs(expected);
        assertThat(response.isActive()).isFalse();
        assertThat(response.isFractionalQuantityAllowedLocked()).isTrue();
    }

    @Test
    void baseUomChangeBeforeFirstMovementSucceeds() {
        Item item = item(42L, true, false);
        UpdateItemRequest request = UpdateItemRequest.builder()
            .baseUnitOfMeasure(UnitOfMeasure.LITER)
            .build();
        stubSuccessfulUpdate(item, request, false);

        service.updateItem("ITEM-1", request);

        verify(stockMovementService).validateMeasurementRuleChanges(item, UnitOfMeasure.LITER, null);
        verify(itemMapper).updateRequestToEntity(request, item);
    }

    @Test
    void baseUomChangeAfterFirstMovementIsRejected() {
        Item item = item(42L, true, false);
        UpdateItemRequest request = UpdateItemRequest.builder()
            .baseUnitOfMeasure(UnitOfMeasure.LITER)
            .build();
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(Optional.of(item));
        doThrow(new BaseUnitOfMeasureImmutableException("ITEM-1"))
            .when(stockMovementService)
            .validateMeasurementRuleChanges(item, UnitOfMeasure.LITER, null);

        assertThatThrownBy(() -> service.updateItem("ITEM-1", request))
            .isInstanceOf(BaseUnitOfMeasureImmutableException.class);

        verify(itemMapper, never()).updateRequestToEntity(request, item);
        verify(itemRepository, never()).saveAndFlush(item);
    }

    @Test
    void fractionalPolicyChangeBeforeFirstMovementSucceeds() {
        Item item = item(42L, true, false);
        UpdateItemRequest request = UpdateItemRequest.builder()
            .fractionalQuantityAllowed(true)
            .build();
        stubSuccessfulUpdate(item, request, false);

        service.updateItem("ITEM-1", request);

        verify(stockMovementService).validateMeasurementRuleChanges(item, null, true);
        verify(itemMapper).updateRequestToEntity(request, item);
    }

    @Test
    void fractionalPolicyChangeAfterFirstMovementIsRejected() {
        Item item = item(42L, true, false);
        UpdateItemRequest request = UpdateItemRequest.builder()
            .fractionalQuantityAllowed(true)
            .build();
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(Optional.of(item));
        doThrow(new FractionalQuantityPolicyImmutableException("ITEM-1"))
            .when(stockMovementService)
            .validateMeasurementRuleChanges(item, null, true);

        assertThatThrownBy(() -> service.updateItem("ITEM-1", request))
            .isInstanceOf(FractionalQuantityPolicyImmutableException.class);

        verify(itemMapper, never()).updateRequestToEntity(request, item);
        verify(itemRepository, never()).saveAndFlush(item);
    }

    @Test
    void unchangedMeasurementRulesAfterMovementAreAccepted() {
        Item item = item(42L, true, false);
        UpdateItemRequest request = UpdateItemRequest.builder()
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(false)
            .build();
        ItemResponse expected = stubSuccessfulUpdate(item, request, true);

        ItemResponse response = service.updateItem("ITEM-1", request);

        assertThat(response).isSameAs(expected);
        verify(itemMapper).itemToItemResponse(item, true);
    }

    private ItemResponse stubSuccessfulUpdate(
        Item item,
        UpdateItemRequest request,
        boolean hasStockMovements
    ) {
        ItemResponse response = ItemResponse.builder()
            .sku(item.getSku())
            .hasStockMovements(hasStockMovements)
            .build();
        when(itemRepository.findItemBySku(item.getSku())).thenReturn(Optional.of(item));
        when(stockMovementService.validateMeasurementRuleChanges(
            item,
            request.getBaseUnitOfMeasure(),
            request.getFractionalQuantityAllowed()
        )).thenReturn(hasStockMovements);
        when(itemRepository.saveAndFlush(item)).thenReturn(item);
        when(itemMapper.itemToItemResponse(item, hasStockMovements)).thenReturn(response);
        return response;
    }

    private Item item(Long id, boolean active, boolean fractionalQuantityAllowed) {
        return Item.builder()
            .id(id)
            .sku("ITEM-1")
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(fractionalQuantityAllowed)
            .stockStore(BigDecimal.ZERO)
            .stockWarehouse(BigDecimal.ZERO)
            .active(active)
            .build();
    }
}

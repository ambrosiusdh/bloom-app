package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.request.item.FilterItemRequest;
import com.bloom.app.api.dto.request.item.UpdateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.exception.StockConcurrencyException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.persistence.repository.ItemCategoryCounterRepository;
import com.bloom.app.persistence.repository.ItemCategoryRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.service.ItemService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.ItemMapper;
import com.bloom.app.service.specification.ItemSpecification;
import com.bloom.app.service.util.PdfGeneratorUtil;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemCategoryCounterRepository itemCategoryCounterRepository;
    private final ItemMapper itemMapper;
    private final PdfGeneratorUtil pdfGeneratorUtil;
    private final StockMovementService stockMovementService;

    @Override
    @Transactional
    public ItemResponse createItem(CreateItemRequest request) {
        log.debug("ItemService createItem using request: {}", request);
        ItemCategory itemCategory = itemCategoryRepository.findByCode(request.getCategoryCode())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ITEM_CATEGORY_NOT_FOUND.getMessage()));

        String sku;
        if (request.getSku() == null) {
            sku = generateSku(itemCategory);
        } else {
            sku = request.getSku();
        }

        boolean fractionalQuantityAllowed = Boolean.TRUE.equals(request.getFractionalQuantityAllowed());
        BigDecimal openingStore = openingBalanceOrZero(request.getStockStore());
        BigDecimal openingWarehouse = openingBalanceOrZero(request.getStockWarehouse());
        InventoryQuantityValidator.validateStock(openingStore, fractionalQuantityAllowed);
        InventoryQuantityValidator.validateStock(openingWarehouse, fractionalQuantityAllowed);

        Item item = itemMapper.createRequestToEntity(request);
        assertZeroInitialBalances(item);
        item.setCategory(itemCategory);
        item.setSku(sku);
        Item savedItem = itemRepository.saveAndFlush(item);
        recordOpeningBalance(savedItem, openingStore, StockLocation.STORE);
        recordOpeningBalance(savedItem, openingWarehouse, StockLocation.WAREHOUSE);
        return itemMapper.itemToItemResponse(savedItem);
    }

    @Override
    @Transactional
    public ItemResponse updateItem(String sku, UpdateItemRequest request) {
        log.debug("ItemService updateItem using request: {}", request);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        stockMovementService.validateBaseUnitOfMeasureChange(item, request.getBaseUnitOfMeasure());
        itemMapper.updateRequestToEntity(request, item);
        InventoryQuantityValidator.validateStock(item.getStockStore(), item.isFractionalQuantityAllowed());
        InventoryQuantityValidator.validateStock(item.getStockWarehouse(), item.isFractionalQuantityAllowed());
        try {
            return itemMapper.itemToItemResponse(itemRepository.saveAndFlush(item));
        } catch (OptimisticLockingFailureException exception) {
            throw new StockConcurrencyException(item.getSku(), exception);
        }
    }

    @Override
    @Transactional
    public void deactivateItem(String sku) {
        log.debug("ItemService deactivateItem using request: {}", sku);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        item.setActive(false);
        itemRepository.save(item);
    }

    @Override
    public Page<ItemResponse> filterItems(FilterItemRequest request, Pageable pageable) {
        Page<Item> itemPage = itemRepository.findAll(ItemSpecification.filter(request), pageable);

        List<ItemResponse> itemResponseList = itemPage.getContent()
            .stream()
            .map(itemMapper::itemToItemResponse)
            .toList();

        return new PageImpl<>(itemResponseList, pageable, itemPage.getTotalElements());
    }

    @Override
    public ItemResponse getItemDetails(String sku) {
        log.debug("ItemService getItemDetails using request: {}", sku);
        return itemRepository.findItemBySku(sku)
            .map(itemMapper::itemToItemResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
    }

    @Transactional
    @Override
    public String generateSku(ItemCategory itemCategory) {
        long nextSequence = itemCategoryCounterRepository.incrementAndGetSequence(itemCategory.getId());
        return String.format("%s-%05d", itemCategory.getCode(), nextSequence);
    }

    @Override
    public byte[] generateSingleBarcodePdf(String sku) {
        log.debug("ItemService generateSingleBarcodePdf using sku: {}", sku);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        return pdfGeneratorUtil.generateBarcodeLayoutPdf(List.of(item));
    }

    @Override
    public byte[] generateBulkBarcodePdf(List<String> skus) {
        log.debug("ItemService generateBulkBarcodePdf using skus: {}", skus);
        if (skus == null || skus.isEmpty()) {
            throw new IllegalArgumentException("SKU list cannot be empty");
        }

        List<Item> items = itemRepository.findBySkuIn(skus);
        long uniqueSkusCount = skus.stream().distinct().count();

        if (uniqueSkusCount != items.size()) {
            List<String> foundSkus = items.stream()
                .map(Item::getSku)
                .toList();
            List<String> missingSkus = skus.stream()
                .filter(sku -> !foundSkus.contains(sku))
                .toList();
            throw new ResourceNotFoundException("Could not generate bulk labels. Missing SKUs: " + missingSkus);
        }

        Map<String, Item> itemMap = items.stream()
            .collect(Collectors.toMap(Item::getSku, item -> item));
        List<Item> sortedItems = skus.stream()
            .map(itemMap::get)
            .toList();

        return pdfGeneratorUtil.generateBarcodeLayoutPdf(sortedItems);
    }

    private void recordOpeningBalance(Item item, BigDecimal quantity, StockLocation stockLocation) {
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            stockMovementService.recordMovement(
                MovementSourceType.OPENING_BALANCE,
                item.getId(),
                item,
                quantity,
                MovementType.IN,
                item.getSku(),
                stockLocation
            );
        }
    }

    private BigDecimal openingBalanceOrZero(BigDecimal quantity) {
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    private void assertZeroInitialBalances(Item item) {
        BigDecimal store = item.getStockStore() == null ? BigDecimal.ZERO : item.getStockStore();
        BigDecimal warehouse = item.getStockWarehouse() == null ? BigDecimal.ZERO : item.getStockWarehouse();
        if (store.compareTo(BigDecimal.ZERO) != 0 || warehouse.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("New items must be persisted with zero stock balances");
        }
    }
}

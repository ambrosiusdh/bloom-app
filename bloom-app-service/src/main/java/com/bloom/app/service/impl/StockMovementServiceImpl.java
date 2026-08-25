package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.exception.BaseUnitOfMeasureImmutableException;
import com.bloom.app.domain.exception.FractionalQuantityPolicyImmutableException;
import com.bloom.app.domain.exception.InsufficientStockException;
import com.bloom.app.domain.exception.StockConcurrencyException;
import com.bloom.app.domain.model.*;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;

    /**
     * Generic method to record a stock movement.
     * Updates the item balance and records the authoritative StockMovement ledger entry.
     */
    @Transactional
    public void recordMovement(
        MovementSourceType sourceType,
        Long sourceId,
        Item item,
        BigDecimal quantity,
        MovementType movementType,
        String referenceNo,
        StockLocation stockLocation
    ) {
        recordMovement(
            sourceType, sourceId, item, quantity, movementType, referenceNo, stockLocation, null);
    }

    private StockMovement recordMovement(
        MovementSourceType sourceType,
        Long sourceId,
        Item item,
        BigDecimal quantity,
        MovementType movementType,
        String referenceNo,
        StockLocation stockLocation,
        StockAdjustmentActionType adjustmentActionType
    ) {
        if (item == null) {
            throw new IllegalArgumentException("Item is required");
        }
        if (item.getId() == null) {
            throw new IllegalArgumentException("Item must be persisted before recording stock");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("Movement source type is required");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("Movement source id is required");
        }
        if (movementType == null) {
            throw new IllegalArgumentException("Movement type is required");
        }
        if (stockLocation == null) {
            throw new IllegalArgumentException("Stock location is required");
        }
        if (referenceNo == null || referenceNo.isBlank()) {
            throw new IllegalArgumentException("Movement reference is required");
        }
        if (referenceNo.length() > 100) {
            throw new IllegalArgumentException("Movement reference must not exceed 100 characters");
        }
        log.debug("Recording movement: item={}, qty={}, type={}, source={}, location={}", item.getSku(), quantity, movementType,
                sourceType, stockLocation);

        InventoryQuantityValidator.validateIncoming(quantity, item.isFractionalQuantityAllowed());

        BigDecimal previousStock = stockAt(item, stockLocation);
        BigDecimal signedEffect = movementType == MovementType.IN ? quantity : quantity.negate();
        BigDecimal newStock = previousStock.add(signedEffect);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientStockException(item.getSku(), stockLocation);
        }
        InventoryQuantityValidator.validateStock(newStock, item.isFractionalQuantityAllowed());

        // 1. Update Product Stock (Performance Optimization)
        if (stockLocation == StockLocation.STORE) {
            item.setStockStore(newStock);
        } else {
            item.setStockWarehouse(newStock);
        }
        Item persistedItem;
        try {
            persistedItem = itemRepository.saveAndFlush(item);
        } catch (OptimisticLockingFailureException exception) {
            throw new StockConcurrencyException(item.getSku(), exception);
        }

        // 2. Create StockMovement (Ledger)
        StockMovement movement = StockMovement.builder()
                .product(persistedItem)
                .movementType(movementType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .referenceNo(referenceNo)
                .adjustmentActionType(adjustmentActionType)
                .quantity(quantity)
                .stockLocation(stockLocation)
                .qtyBefore(previousStock)
                .qtyAfter(newStock)
                .build();
        return stockMovementRepository.saveAndFlush(movement);
    }

    // Helper to record sales
    @Transactional
    public void recordSaleMovements(Sale sale) {
        if (sale == null || sale.getId() == null) {
            throw new IllegalArgumentException("Sale must be persisted before recording stock");
        }
        Map<StockKey, SaleMovement> movements = new LinkedHashMap<>();
        for (SaleItem saleItem : sale.getItems()) {
            if (saleItem == null || saleItem.getItem() == null || saleItem.getItem().getId() == null) {
                throw new IllegalArgumentException("Persisted sale item is required");
            }
            InventoryQuantityValidator.validateIncoming(
                saleItem.getQuantity(), saleItem.getItem().isFractionalQuantityAllowed());
            StockKey key = new StockKey(saleItem.getItem().getId(), saleItem.getStockLocation());
            movements.merge(
                key,
                new SaleMovement(saleItem.getItem(), saleItem.getStockLocation(), saleItem.getQuantity()),
                (existing, incoming) -> new SaleMovement(
                    existing.item(),
                    existing.stockLocation(),
                    existing.quantity().add(incoming.quantity()))
            );
        }

        for (SaleMovement movement : movements.values()) {
            boolean alreadyRecorded = stockMovementRepository
                .existsBySourceTypeAndSourceIdAndProduct_IdAndStockLocation(
                    MovementSourceType.SALE,
                    sale.getId(),
                    movement.item().getId(),
                    movement.stockLocation()
                );
            if (alreadyRecorded) {
                continue;
            }
            recordMovement(
                    MovementSourceType.SALE,
                    sale.getId(),
                    movement.item(),
                    movement.quantity(),
                    MovementType.OUT,
                    sale.getCode(),
                    movement.stockLocation()
            );
        }
    }

    @Override
    @Transactional
    public void recordGoodsReceiptPosting(GoodsReceipt receipt) {
        recordGoodsReceiptMovements(
            receipt, MovementSourceType.GOODS_RECEIPT, MovementType.IN);
    }

    @Override
    @Transactional
    public void recordGoodsReceiptCancellation(GoodsReceipt receipt) {
        recordGoodsReceiptMovements(
            receipt, MovementSourceType.GOODS_RECEIPT_CANCELLATION, MovementType.OUT);
    }

    // Helper for Manual Adjustments
    @Transactional
    public List<StockMovement> recordManualAdjustment(StockAdjustment adjustment) {
        if (adjustment == null || adjustment.getId() == null) {
            throw new IllegalArgumentException("Stock adjustment must be persisted before recording stock");
        }
        List<StockMovement> persistedMovements = new ArrayList<>();
        for (StockAdjustmentItem item : adjustment.getItems()) {
            AdjustmentMovement movement = adjustmentMovement(item);
            persistedMovements.add(recordMovement(
                MovementSourceType.STOCK_ADJUSTMENT,
                adjustment.getId(),
                item.getItem(),
                movement.quantity(),
                movement.movementType(),
                adjustment.getStockAdjustmentCode(),
                item.getStockLocation(),
                item.getActionType()
            ));
        }
        return List.copyOf(persistedMovements);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateMeasurementRuleChanges(
        Item item,
        UnitOfMeasure requestedUnitOfMeasure,
        Boolean requestedFractionalQuantityAllowed
    ) {
        if (item == null || item.getId() == null) {
            return false;
        }

        boolean hasStockMovements = hasStockMovements(item.getId());
        if (!hasStockMovements) {
            return false;
        }
        if (requestedUnitOfMeasure != null
                && requestedUnitOfMeasure != item.getBaseUnitOfMeasure()) {
            throw new BaseUnitOfMeasureImmutableException(item.getSku());
        }
        if (requestedFractionalQuantityAllowed != null
                && requestedFractionalQuantityAllowed != item.isFractionalQuantityAllowed()) {
            throw new FractionalQuantityPolicyImmutableException(item.getSku());
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasStockMovements(Long itemId) {
        return itemId != null && stockMovementRepository.existsByProductId(itemId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findItemIdsWithStockMovements(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Collections.emptySet();
        }
        return stockMovementRepository.findProductIdsWithMovements(itemIds);
    }

    private BigDecimal stockAt(Item item, StockLocation stockLocation) {
        BigDecimal stock = switch (stockLocation) {
            case STORE -> item.getStockStore();
            case WAREHOUSE -> item.getStockWarehouse();
        };
        return stock == null ? BigDecimal.ZERO : stock;
    }

    private void recordGoodsReceiptMovements(
            GoodsReceipt receipt,
            MovementSourceType sourceType,
            MovementType movementType) {
        if (receipt == null || receipt.getId() == null) {
            throw new IllegalArgumentException("Goods receipt must be persisted before recording stock");
        }
        Map<StockKey, ReceiptMovement> movements = new LinkedHashMap<>();
        for (GoodsReceiptItem line : receipt.getItems()) {
            if (line == null || line.getItem() == null || line.getItem().getId() == null) {
                throw new IllegalArgumentException("Persisted goods receipt line is required");
            }
            InventoryQuantityValidator.validateIncoming(
                line.getQuantity(), line.getItem().isFractionalQuantityAllowed());
            StockKey key = new StockKey(line.getItem().getId(), line.getStockLocation());
            movements.merge(
                key,
                new ReceiptMovement(line.getItem(), line.getStockLocation(), line.getQuantity()),
                (existing, incoming) -> new ReceiptMovement(
                    existing.item(),
                    existing.stockLocation(),
                    existing.quantity().add(incoming.quantity()))
            );
        }
        movements.values().stream()
            .sorted(java.util.Comparator
                .comparing((ReceiptMovement movement) -> movement.item().getId())
                .thenComparing(movement -> movement.stockLocation().name()))
            .forEach(movement -> {
                boolean alreadyRecorded = stockMovementRepository
                    .existsBySourceTypeAndSourceIdAndProduct_IdAndStockLocation(
                        sourceType,
                        receipt.getId(),
                        movement.item().getId(),
                        movement.stockLocation()
                    );
                if (!alreadyRecorded) {
                    recordMovement(
                        sourceType,
                        receipt.getId(),
                        movement.item(),
                        movement.quantity(),
                        movementType,
                        receipt.getCode(),
                        movement.stockLocation()
                    );
                }
            });
    }

    private AdjustmentMovement adjustmentMovement(StockAdjustmentItem item) {
        if (item == null || item.getItem() == null || item.getActionType() == null) {
            throw new IllegalArgumentException("Complete stock adjustment item is required");
        }
        InventoryQuantityValidator.validateStock(
            item.getPreviousStock(), item.getItem().isFractionalQuantityAllowed());
        InventoryQuantityValidator.validateStock(
            item.getNewStock(), item.getItem().isFractionalQuantityAllowed());

        AdjustmentMovement movement = switch (item.getActionType()) {
            case ADD -> {
                InventoryQuantityValidator.validateIncoming(
                    item.getChangeQuantity(), item.getItem().isFractionalQuantityAllowed());
                yield new AdjustmentMovement(MovementType.IN, item.getChangeQuantity());
            }
            case REMOVE -> {
                InventoryQuantityValidator.validateIncoming(
                    item.getChangeQuantity(), item.getItem().isFractionalQuantityAllowed());
                yield new AdjustmentMovement(MovementType.OUT, item.getChangeQuantity());
            }
            case CORRECTION -> {
                InventoryQuantityValidator.validateStock(
                    item.getChangeQuantity(), item.getItem().isFractionalQuantityAllowed());
                if (item.getChangeQuantity().compareTo(item.getNewStock()) != 0) {
                    throw new IllegalStateException("Correction target must equal resulting stock");
                }
                BigDecimal delta = item.getNewStock().subtract(item.getPreviousStock());
                if (delta.signum() == 0) {
                    throw new IllegalArgumentException(
                        "Correction target must differ from current stock");
                }
                yield new AdjustmentMovement(
                    delta.signum() >= 0 ? MovementType.IN : MovementType.OUT,
                    delta.abs());
            }
        };

        BigDecimal expectedNewStock = movement.movementType() == MovementType.IN
            ? item.getPreviousStock().add(movement.quantity())
            : item.getPreviousStock().subtract(movement.quantity());
        if (expectedNewStock.compareTo(item.getNewStock()) != 0) {
            throw new IllegalStateException("Stock adjustment balance snapshots are inconsistent");
        }
        return movement;
    }

    private record StockKey(Long itemId, StockLocation stockLocation) {
    }

    private record SaleMovement(Item item, StockLocation stockLocation, BigDecimal quantity) {
    }

    private record ReceiptMovement(Item item, StockLocation stockLocation, BigDecimal quantity) {
    }

    private record AdjustmentMovement(MovementType movementType, BigDecimal quantity) {
    }
}
